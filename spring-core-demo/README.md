# spring-core-demo

一个专门讲 **Spring IoC / Bean 生命周期 / AOP 代理 / 循环依赖** 的教学项目。

这个项目重点不是业务，而是把面试里常见的 Spring 原理题用最小代码拆开：

1. Bean 是什么时候创建的
2. `@PostConstruct`、`afterPropertiesSet`、`@PreDestroy` 谁先谁后
3. `@Transactional`、AOP 为什么依赖代理
4. `@Service` 为什么会变成容器里的 BeanDefinition
5. 循环依赖为什么有的能解、有的不能解

---

## 这个项目讲什么

### 1. Bean 生命周期 demo

对应代码：

- `lifecycle/LifecycleRecorder.java`
- `lifecycle/LifecycleProbe.java`

会直接演示：

- 构造器回调
- `@PostConstruct`
- `InitializingBean.afterPropertiesSet`
- `@PreDestroy`
- `DisposableBean.destroy`

### 2. AOP / 代理 demo

对应代码：

- `proxy/ProxyTargetService.java`
- `proxy/TracingAspect.java`

会直接演示：

- 调用为什么会先经过切面
- 目标方法和 Advice 的先后顺序
- 为什么“通过代理调用”和“类内自调用”是两回事

### 3. IoC 与循环依赖 demo

重点看测试：

- `ioc/BeanDefinitionRegistrationDemoTest`
- `circular/CircularDependencyDemoTest`

会直接演示：

- component scan 先注册 BeanDefinition，再创建 Bean
- setter 风格依赖能组装起来
- constructor 风格循环依赖会在容器刷新时失败

---

## 这个项目怎么学

建议按这个顺序看：

1. `LifecycleProbe`
2. `ProxyTargetService`
3. `BeanDefinitionRegistrationDemoTest`
4. `CircularDependencyDemoTest`
5. `demo/DemoRunner.java`

---

## 如何运行

```bash
mvn spring-boot:run
```

启动后会打印：

1. Bean 生命周期执行顺序
2. AOP 切面前后拦截顺序

---

## 如何运行测试

```bash
mvn test
```

重点看：

- `LifecycleProbeTest`
- `ProxyTargetServiceTest`
- `BeanDefinitionRegistrationDemoTest`
- `CircularDependencyDemoTest`

---

## 面试里怎么说最稳

### 1. `@Service` 为什么能进容器？

> 因为 Spring 在 component scan 阶段先扫描类上的注解，把元数据包装成 BeanDefinition 注册到 BeanFactory，后面容器 refresh 时才真正实例化 Bean。

### 2. 为什么 AOP 能拦到方法？

> 因为容器里放进去的通常不是原始对象，而是一个代理对象。外部通过代理调用时，Advice 有机会织进去；类内 `this.xxx()` 调用绕过代理，所以像 `@Transactional` 这类基于代理的能力就可能失效。

### 3. 循环依赖为什么 constructor 更容易炸？

> 因为构造器注入要求对象在创建时就把依赖准备好，A 造不出来要 B，B 造不出来又要 A，容器没法提前暴露一个半成品对象。
