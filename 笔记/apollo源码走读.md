# Apollo源码走读

 apollo 由于实现了ApplicationContextInitializer初始化接口，所以在Springboot应用初始化阶段被触发调用，

![image-20200830121251755](.\img\apollo\apollo3.jpg)

```java
protected void initialize(ConfigurableEnvironment environment) {
    if (environment.getPropertySources().contains(PropertySourcesConstants.APOLLO_BOOTSTRAP_PROPERTY_SOURCE_NAME)) {
      //already initialized
      return;
    }

    //从bootstrap配置文件读取配置的apollo所有配置的命名空间列表
    String namespaces = environment.getProperty(PropertySourcesConstants.APOLLO_BOOTSTRAP_NAMESPACES, ConfigConsts.NAMESPACE_APPLICATION);
    logger.debug("Apollo bootstrap namespaces: {}", namespaces);
    List<String> namespaceList = NAMESPACE_SPLITTER.splitToList(namespaces);

    //apollo自己的PropertySource实现类，是一个复合PropertySource，里面聚合了其他PropertySource
    CompositePropertySource composite = new CompositePropertySource(PropertySourcesConstants.APOLLO_BOOTSTRAP_PROPERTY_SOURCE_NAME);
    for (String namespace : namespaceList) {
        //开始获取每个命名空间下的配置
      Config config = ConfigService.getConfig(namespace);

      /**
        将所有命名空间的所有配置都聚合到composite这个复合属性源对象里。
 		**/     composite.addPropertySource(configPropertySourceFactory.getConfigPropertySource(namespace, config));
        //由于CompositePropertySource也实现了PropertySource接口，所以可以直接将其加入到Spring environment中的propertySources中，再配置各种类时，就会从各个ConfigPropertySource查找属性值了
        environment.getPropertySources().addFirst(composite);
    }
```

```java
/**{@ConfigService}**/

    //根据名称空间获取对应的Config对象
public static Config getConfig(String namespace) {
    return s_instance.getManager().getConfig(namespace);
  }


/**
{@linkplain DefaultConfigFactory}
**/
@Override
  public Config create(String namespace) {
    ConfigFileFormat format = determineFileFormat(namespace);
    if (ConfigFileFormat.isPropertiesCompatible(format)) {
      return new DefaultConfig(namespace, 
                               //创建属性兼容文件配置存储库
                               createPropertiesCompatibleFileConfigRepository(namespace, format));
    }
    return new DefaultConfig(namespace, createLocalConfigRepository(namespace));
  }
```

```java
PropertiesCompatibleFileConfigRepository createPropertiesCompatibleFileConfigRepository(String namespace,
      ConfigFileFormat format) {
    String actualNamespaceName = trimNamespaceFormat(namespace, format);
    PropertiesCompatibleConfigFile configFile = (PropertiesCompatibleConfigFile) ConfigService
        //获取配置文件
        .getConfigFile(actualNamespaceName, format);

    return new PropertiesCompatibleFileConfigRepository(configFile);
  }
```

```java
@Override
  public ConfigFile getConfigFile(String namespace, ConfigFileFormat configFileFormat) {
    String namespaceFileName = String.format("%s.%s", namespace, configFileFormat.getValue());
    ConfigFile configFile = m_configFiles.get(namespaceFileName);

    if (configFile == null) {
      synchronized (this) {
          //线程安全查看本地是否缓存了ConfigFile对象
        configFile = m_configFiles.get(namespaceFileName);
        if (configFile == null) {
          ConfigFactory factory = m_factoryManager.getFactory(namespaceFileName);
          //没有的化，调用工厂类创建对象
          configFile = factory.createConfigFile(namespaceFileName, configFileFormat);
          m_configFiles.put(namespaceFileName, configFile);
        }
      }
    }

    return configFile;
  }
```

```java
@Override
  public ConfigFile createConfigFile(String namespace, ConfigFileFormat configFileFormat) {
      //创建本地命名空间配置库，即使是远程模式，也是封装成本地模式，即将远程配置数据本地保留一份。
    ConfigRepository configRepository = createLocalConfigRepository(namespace);
      //适配不同文件后缀类型
    switch (configFileFormat) {
      case Properties:
        return new PropertiesConfigFile(namespace, configRepository);
      case XML:
        return new XmlConfigFile(namespace, configRepository);
      case JSON:
        return new JsonConfigFile(namespace, configRepository);
      case YAML:
        return new YamlConfigFile(namespace, configRepository);
      case YML:
        return new YmlConfigFile(namespace, configRepository);
    }
```

```java
LocalFileConfigRepository createLocalConfigRepository(String namespace) {
    //根据配置是否开启本地模式
    if (m_configUtil.isInLocalMode()) {
      logger.warn(
          "==== Apollo is in local mode! Won't pull configs from remote server for namespace {} ! ====",
          namespace);
        //从本地文件读取配置数据
      return new LocalFileConfigRepository(namespace);
    }
    
    //LocalFileConfigRepository包装RemoteConfigRepository对象，本地也保留一份配置文件
    return new LocalFileConfigRepository(namespace, 
                                         //从远端拉取配置数据
                                         createRemoteConfigRepository(namespace));
  }
```

```java
 public RemoteConfigRepository(String namespace) {
    m_namespace = namespace;
    m_configCache = new AtomicReference<>();
    m_configUtil = ApolloInjector.getInstance(ConfigUtil.class);
    m_httpUtil = ApolloInjector.getInstance(HttpUtil.class);
    m_serviceLocator = ApolloInjector.getInstance(ConfigServiceLocator.class);
    remoteConfigLongPollService = ApolloInjector.getInstance(RemoteConfigLongPollService.class);
    m_longPollServiceDto = new AtomicReference<>();
    m_remoteMessages = new AtomicReference<>();
    m_loadConfigRateLimiter = RateLimiter.create(m_configUtil.getLoadConfigQPS());
    m_configNeedForceRefresh = new AtomicBoolean(true);
    m_loadConfigFailSchedulePolicy = new ExponentialSchedulePolicy(m_configUtil.getOnErrorRetryInterval(),
        m_configUtil.getOnErrorRetryInterval() * 8);
    gson = new Gson();
     //调用父类AbstractConfigRepository的同步配置方法，这个方法会调用子类sync方法开始同步
    this.trySync();
    this.schedulePeriodicRefresh();
     //定时长轮询刷
    this.scheduleLongPollingRefresh();
  }
```

```java
  @Override
  protected synchronized void sync() {
      //开启事务
    Transaction transaction = Tracer.newTransaction("Apollo.ConfigService", "syncRemoteConfig");

    try {
        //本地缓存的旧配置
      ApolloConfig previous = m_configCache.get();
        //加载远端服务器上的配置数据
      ApolloConfig current = loadApolloConfig();

      //reference equals means HTTP 304
      if (previous != current) {
        logger.debug("Remote Config refreshed!");
          //将远端拉取到的配置数据缓存起来
        m_configCache.set(current);
          //如果数据发生变更，触发对应事件，告诉监听方
        this.fireRepositoryChange(m_namespace, this.getConfig());
      }

      if (current != null) {
        Tracer.logEvent(String.format("Apollo.Client.Configs.%s", current.getNamespaceName()),
            current.getReleaseKey());
      }

      transaction.setStatus(Transaction.SUCCESS);
    } catch (Throwable ex) {
      transaction.setStatus(ex);
      throw ex;
    } finally {
      transaction.complete();
    }
  }
```

```java
private ApolloConfig loadApolloConfig() {
    String appId = m_configUtil.getAppId();
    String cluster = m_configUtil.getCluster();
    String dataCenter = m_configUtil.getDataCenter();
    Tracer.logEvent("Apollo.Client.ConfigMeta", STRING_JOINER.join(appId, cluster, m_namespace));
    int maxRetries = m_configNeedForceRefresh.get() ? 2 : 1;
    long onErrorSleepTime = 0; // 0 means no sleep
    Throwable exception = null;

    //获取服务器列表
    List<ServiceDTO> configServices = getConfigServices();
    String url = null;
    for (int i = 0; i < maxRetries; i++) {
      List<ServiceDTO> randomConfigServices = Lists.newLinkedList(configServices);
      Collections.shuffle(randomConfigServices);
      //Access the server which notifies the client first
      if (m_longPollServiceDto.get() != null) {
        randomConfigServices.add(0, m_longPollServiceDto.getAndSet(null));
      }

      for (ServiceDTO configService : randomConfigServices) {
        if (onErrorSleepTime > 0) {
          logger.warn(
              "Load config failed, will retry in {} {}. appId: {}, cluster: {}, namespaces: {}",
              onErrorSleepTime, m_configUtil.getOnErrorRetryIntervalTimeUnit(), appId, cluster, m_namespace);

          try {
            m_configUtil.getOnErrorRetryIntervalTimeUnit().sleep(onErrorSleepTime);
          } catch (InterruptedException e) {
            //ignore
          }
        }
        
         //请求地址
        url = assembleQueryConfigUrl(configService.getHomepageUrl(), appId, cluster, m_namespace,
                dataCenter, m_remoteMessages.get(), m_configCache.get());

        logger.debug("Loading config from {}", url);
        HttpRequest request = new HttpRequest(url);

        Transaction transaction = Tracer.newTransaction("Apollo.ConfigService", "queryConfig");
        transaction.addData("Url", url);
        try {
          //http请求服务器上的配置数据
          HttpResponse<ApolloConfig> response = m_httpUtil.doGet(request, ApolloConfig.class);
          m_configNeedForceRefresh.set(false);
          m_loadConfigFailSchedulePolicy.success();

          transaction.addData("StatusCode", response.getStatusCode());
          transaction.setStatus(Transaction.SUCCESS);

          if (response.getStatusCode() == 304) {
            logger.debug("Config server responds with 304 HTTP status code.");
            return m_configCache.get();
          }

          ApolloConfig result = response.getBody();

          logger.debug("Loaded config for {}: {}", m_namespace, result);

          return result;
        } catch (ApolloConfigStatusCodeException ex) {
          ApolloConfigStatusCodeException statusCodeException = ex;
          //config not found
          if (ex.getStatusCode() == 404) {
            String message = String.format(
                "Could not find config for namespace - appId: %s, cluster: %s, namespace: %s, " +
                    "please check whether the configs are released in Apollo!",
                appId, cluster, m_namespace);
            statusCodeException = new ApolloConfigStatusCodeException(ex.getStatusCode(),
                message);
          }
          Tracer.logEvent("ApolloConfigException", ExceptionUtil.getDetailMessage(statusCodeException));
          transaction.setStatus(statusCodeException);
          exception = statusCodeException;
        } catch (Throwable ex) {
          Tracer.logEvent("ApolloConfigException", ExceptionUtil.getDetailMessage(ex));
          transaction.setStatus(ex);
          exception = ex;
        } finally {
          transaction.complete();
        }

        // if force refresh, do normal sleep, if normal config load, do exponential sleep
        onErrorSleepTime = m_configNeedForceRefresh.get() ? m_configUtil.getOnErrorRetryInterval() :
            m_loadConfigFailSchedulePolicy.fail();
      }

    }
    String message = String.format(
        "Load Apollo Config failed - appId: %s, cluster: %s, namespace: %s, url: %s",
        appId, cluster, m_namespace, url);
    throw new ApolloConfigException(message, exception);
  }
```

```java

//apollo客户端长轮询获取服务器端通知，有变更通知后再同步配置数据，再触发客户端的监听器。
private void doLongPollingRefresh(String appId, String cluster, String dataCenter) {
    final Random random = new Random();
    ServiceDTO lastServiceDto = null;
    while (!m_longPollingStopped.get() && !Thread.currentThread().isInterrupted()) {
      String url = null;
      try {
          //负载均衡获取服务器实例
        if (lastServiceDto == null) {
          List<ServiceDTO> configServices = getConfigServices();
          lastServiceDto = configServices.get(random.nextInt(configServices.size()));
        }

        url =
            assembleLongPollRefreshUrl(lastServiceDto.getHomepageUrl(), appId, cluster, dataCenter,
                m_notifications);

        HttpRequest request = new HttpRequest(url);
          //设置长超时时间，默认90 秒，应该比服务器端的长轮询超时长，服务器现在是 60 秒
        request.setReadTimeout(LONG_POLLING_READ_TIMEOUT);

        transaction.addData("Url", url);

        final HttpResponse<List<ApolloConfigNotification>> response =
            m_httpUtil.doGet(request, m_responseType);

        logger.debug("Long polling response: {}, url: {}", response.getStatusCode(), url);
        if (response.getStatusCode() == 200 && response.getBody() != null) {
          updateNotifications(response.getBody());
          updateRemoteNotifications(response.getBody());
          transaction.addData("Result", response.getBody().toString());
          notify(lastServiceDto, response.getBody());
        }

        //try to load balance
        if (response.getStatusCode() == 304 && random.nextBoolean()) {
          lastServiceDto = null;
        }

        m_longPollFailSchedulePolicyInSecond.success();
        transaction.addData("StatusCode", response.getStatusCode());
        transaction.setStatus(Transaction.SUCCESS);
      } catch (Throwable ex) {
        lastServiceDto = null;
        Tracer.logEvent("ApolloConfigException", ExceptionUtil.getDetailMessage(ex));
        transaction.setStatus(ex);
        long sleepTimeInSecond = m_longPollFailSchedulePolicyInSecond.fail();
        logger.warn(
            "Long polling failed, will retry in {} seconds. appId: {}, cluster: {}, namespaces: {}, long polling url: {}, reason: {}",
            sleepTimeInSecond, appId, cluster, assembleNamespaces(), url, ExceptionUtil.getDetailMessage(ex));
        try {
          TimeUnit.SECONDS.sleep(sleepTimeInSecond);
        } catch (InterruptedException ie) {
          //ignore
        }
      } finally {
        transaction.complete();
      }
    }
  }
```

![image-20200830121251755](.\img\apollo\apollo4.jpg)



从上图能看到，apollo初始化过程，会根据配置的ConfigSourceType这个枚举类是remote还是local来选择是拉取服务器上的配置数据还是使用本地缓存文件的数据。

![image-20200830121251755](.\img\apollo\apollo5.jpg)

​																	apollo缓存的本地文件



项目首先加上@EnableApolloConfig这个注解，这个注解会引入ApolloConfigRegistrar这个bean注册器，因为他实现了ImportBeanDefinitionRegistrar，那在ConfigurationClassPostProcessor#postProcessBeanFactory会调用用户注册Beandifination，即调用ApolloConfigRegistrar#registerBeanDefinitions的方法，注册apollo相关的组件。



![image-20200830121251755](.\img\apollo\apollo1.jpg)

这个注册器的主要工作是注册跟配置相关的BeanDefination

![image-20200830121251755](.\img\apollo\apollo2.jpg)

