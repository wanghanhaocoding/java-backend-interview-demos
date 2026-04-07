# probe-mq-troubleshooting-demo

一个专门讲 `HTTP probe / readiness` 已经把节点摘出流量，但节点里的 MQ consumer 线程还在继续跑的教学项目。

这版不依赖 Redis、MySQL、Kafka，全部使用：

- `spring-boot-starter-web`
- `spring-boot-starter-actuator`
- 进程内阻塞 HTTP handler
- 进程内 `BlockingQueue` + 独立 MQ consumer 线程池

这样你在 Linux 上只要有 `JDK8 + Maven` 就能把问题跑出来、留证、再按排查路径验证。

## 这个 demo 讲什么

对应代码：

- `http/TrafficController.java`
- `health/HttpPressureHealthIndicator.java`
- `mq/InMemoryMqConsumerService.java`
- `node/NodeScenarioService.java`

会直接演示：

1. 一个节点因为阻塞 HTTP handler 太多，`/actuator/health/readiness` 变成 `DOWN`
2. 同一个节点的 `/api/traffic` 继续卡在 `http-nio-*` 请求线程上
3. 另一个线程池里的 `node-*-mq-consumer-*` 还在继续消费本地内存队列
4. 用 probe-aware 路由脚本后，业务流量会自然绕开 unhealthy 节点，但 MQ 消费日志仍然继续增长

## 你最先看这 5 个文件就够了

1. `http/TrafficController.java`
2. `health/HttpPressureHealthIndicator.java`
3. `mq/InMemoryMqConsumerService.java`
4. `docs/linux-probe-vs-mq-runbook.md`
5. `ProbeMqTroubleshootingDemoTest`

## 如何运行

先起健康节点：

```bash
cd probe-mq-troubleshooting-demo
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=8080 --demo.node-id=node-a"
```

再起故障节点：

```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=8081 --demo.node-id=node-b --demo.fault.enabled=true --demo.fault.block-seconds=25"
```

启动后重点看：

1. `node-b` 的 `/actuator/health/readiness` 会在并发阻塞请求打满阈值后变成 `DOWN`
2. `node-b` 的 MQ 消费日志仍然继续打印
3. 你写的 probe-aware 路由脚本会把业务请求转到 `node-a`

## Linux 服务器手工 runbook

- `docs/linux-probe-vs-mq-runbook.md`

## 如何运行测试

```bash
mvn test
```

重点看：

- `HttpPressureHealthIndicatorTest`
- `InMemoryMqConsumerServiceTest`
- `ProbeMqTroubleshootingDemoTest`

## 面试里怎么说最稳

> 我会把这个问题讲成“同一个 JVM 里，HTTP readiness 已经把节点摘掉，但 MQ consumer 线程池还在继续工作”的隔离案例。因为 HTTP 侧的问题不一定等于整个进程都挂了，如果只是请求线程被阻塞、handler 卡住或者 worker 接近饱和，那么 readiness 可以先变成 `DOWN`，负载均衡随后把这个节点摘流量；但如果 MQ 消费线程池、消息源和业务回调链路仍然独立，它完全可能继续消费消息。这类问题排查时我不会只看 `/actuator/health`，还会同时看节点日志、`jps/jcmd/jstack` 里的线程名，以及 HTTP 请求和 MQ 消费计数是否出现“API 没流量了，但 MQ 还在跑”的分叉。 
