# resilience-auth-demo

一个专门讲 **限流 / 熔断 / 降级 / 隔离 / JWT / RBAC** 的教学项目。

这个项目把“服务治理”和“认证授权”放在一起，因为在 Java 后端面试里，这两类题经常围绕接口稳定性和安全边界连着追问。

---

## 这个项目讲什么

### 1. 服务治理 demo

对应代码：

- `resilience/ResilienceDemoService.java`

会直接演示：

- 固定窗口限流
- 连续失败达到阈值后打开熔断器
- 熔断打开时直接走降级返回
- bulkhead / 线程池隔离如何限制并发入口

### 2. 认证授权 demo

对应代码：

- `auth/AuthDemoService.java`

会直接演示：

- 登录后创建 session
- 生成一个 HMAC 签名的 JWT 风格 token
- 校验 token 是否有效、是否过期、是否被拉黑
- 用 RBAC 判断用户是否拥有权限

---

## 这个项目怎么学

建议按这个顺序看：

1. `ResilienceDemoService`
2. `AuthDemoService`
3. `demo/DemoRunner.java`
4. 各模块测试

---

## 如何运行

```bash
mvn spring-boot:run
```

启动后会按顺序打印：

1. 固定窗口限流效果
2. 熔断与降级
3. bulkhead 隔离
4. session + JWT + RBAC

---

## 如何运行测试

```bash
mvn test
```

重点看：

- `ResilienceDemoTest`
- `AuthDemoTest`

---

## 面试里怎么说最稳

### 1. 限流和熔断的区别是什么？

> 限流是入口控流，目的是别把系统压垮；熔断是发现下游持续失败后暂时不再调用，目的是防止故障扩散。一个是“别进太多”，一个是“别再打了”。

### 2. 降级一般什么时候出现？

> 当限流、熔断或者下游超时后，系统不一定直接报错，可以返回缓存快照、兜底文案、异步受理状态，这就是降级。

### 3. 为什么 JWT 还要配黑名单？

> 因为 JWT 默认是无状态的，签发后服务端不保存会话。如果要做主动下线、踢人、紧急失效，就需要黑名单或短 token + 刷新 token 机制。
