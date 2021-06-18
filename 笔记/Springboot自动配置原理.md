

![image-20200831104852202](.\img\spring\image-20200831104852202.png)

springboot自动配置从**ConfigurationClassPostProcessor**这个后置处理器开始，而这个处理器会在SpringApplication类创建上下文createApplicationContext时，由**AnnotationConfigUtils**这个工具类的**registerAnnotationConfigProcessors**的方法中注册。



刷新应用上下文时，触发后置处理器调用。

```java
public void refresh() throws BeansException, IllegalStateException {
		synchronized (this.startupShutdownMonitor) {
			// Prepare this context for refreshing.
			prepareRefresh();
				******
			try {
				// Allows post-processing of the bean factory in context subclasses.
				postProcessBeanFactory(beanFactory);

				// 调用后置处理器
				invokeBeanFactoryPostProcessors(beanFactory);
```



**ConfigurationClassPostProcessor**类会扫描所有注册的被`@Configuration`注解的bean，然后开始配置java bean的解析。

最终spring的**ConfigurationClassParser**

```java
/**
		 * Return the imports defined by the group.
		 * @return each import with its associated configuration class
		 */
		public Iterable<Group.Entry> getImports() {
			for (DeferredImportSelectorHolder deferredImport : this.deferredImports) {
                //这里最终调用Sprinboot的AutoConfigurationImportSelector
				this.group.process(deferredImport.getConfigurationClass().getMetadata(),
						deferredImport.getImportSelector());
			}
			return this.group.selectImports();
		}
```

![image-20200831114920305](.\img\spring\image-20200831114920305.png)

会调用SpringBoot的**AutoConfigurationImportSelector**类的**process**方法，

![image-20200831115328566](.\img\spring\image-20200831115328566.png)这个方法通过读取类路径/META-INF/spring.factories中键为EnableAutoConfiguration的所有的自动配置类。

```java
@Override
		public void process(AnnotationMetadata annotationMetadata, DeferredImportSelector deferredImportSelector) {
			Assert.state(deferredImportSelector instanceof AutoConfigurationImportSelector,
					() -> String.format("Only %s implementations are supported, got %s",
							AutoConfigurationImportSelector.class.getSimpleName(),
							deferredImportSelector.getClass().getName()));
			AutoConfigurationEntry autoConfigurationEntry = ((AutoConfigurationImportSelector) deferredImportSelector)
					.getAutoConfigurationEntry(annotationMetadata);
            //autoConfigurationEntry包含各种AutoConfigration
			this.autoConfigurationEntries.add(autoConfigurationEntry);
			for (String importClassName : autoConfigurationEntry.getConfigurations()) {
				this.entries.putIfAbsent(importClassName, annotationMetadata);
			}
		}
```



随后**ConfigurationClassParser **  **processImports**方法迭代刚刚得到的所有AutoConfigration类用于配置应用中@Configaration注解的类如启动类，并将其中@Bean注解方法返回的bean注册到容器中。



```java
//解析完后开始从config配置类中加载bean信息
this.reader.loadBeanDefinitions(configClasses);
alreadyParsed.addAll(configClasses);
```

![image-20200830121251755](.\img\spring\image-20200830121251755.png)

![image-20200830121740732](.\img\spring\image-20200830121740732.png)

从俩处加载bean信息，一处是xml等resource文件，一处是各种Bean Registrars。

![image-20200830122035669](.\img\spring\image-20200830122035669.png)

可以看到，这里会调用Springboot定义的**ConfigurationPropertiesScanRegistrar**，然后**ConfigurationPropertiesScanRegistrar**会登记自己想要注册的路径下所有bean对象。

