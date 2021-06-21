# 		SpringBoot  基于注解扫描自动注册Bean

![Spring1.png](.\img\spring\Spring1.jpg)



会导入Spring提供的import 注册器,

![Spring1.png](.\img\spring\Spring2.jpg)

![Spring1.png](.\img\spring\spring3.jpg)

这个注册器会在Spring 执行ConfigurationClassPostProcessor这个BeanFactoryPostProcessor 后执行器时触发，将AutoConfigurationPackage注解修饰的类的路径保存在注册器的BeanDefination中，供其他组件查询使用。

![Spring1.png](.\img\spring\spring4.jpg)

这步的作用主要是将注解标注的类的根路径保存起来，供其他框架使用，例如下面的Mybatis-springboot

读取的mapper扫描路径就是取自这里的。AbstractRepositoryConfigurationSourceSupport  也是这样。

![Spring1.png](.\img\spring\spring6.jpg)

另外像appllo，dubbo都是通过这种自定义Regiter注册器的扩展方式插入spring容器。

![Spring1.png](.\img\spring\spring5.jpg)



### Spring bean生命周期图

![Spring1.png](.\img\spring\spring流程图.jpg)

![Spring1.png](.\img\spring\spring7.jpg)