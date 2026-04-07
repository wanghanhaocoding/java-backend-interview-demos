# Linux 手工 Runbook：probe unhealthy，但 MQ consumer 继续运行

这份 runbook 只做一件事：

在 Linux 机器上，把“节点已经因为 HTTP probe / readiness 失败被摘出流量，但 MQ consumer 线程还在继续消费”的场景完整走一遍。

## 1. 适用前提

- 服务器系统：`Linux`
- JDK：`8`
- Maven：可用
- 当前目录：`probe-mq-troubleshooting-demo`
- 不依赖 Redis / MySQL / Kafka

先确认环境：

```bash
java -version
mvn -version
pwd
```

## 2. 启动两个节点

先起健康节点 `node-a`：

```bash
mkdir -p lab-output/probe-vs-mq
nohup mvn spring-boot:run \
  -Dspring-boot.run.arguments="--server.port=8080 --demo.node-id=node-a --demo.fault.enabled=false" \
  > lab-output/probe-vs-mq/node-a.log 2>&1 &
```

再起故障节点 `node-b`：

```bash
nohup mvn spring-boot:run \
  -Dspring-boot.run.arguments="--server.port=8081 --demo.node-id=node-b --demo.fault.enabled=true --demo.fault.block-seconds=25 --demo.readiness.down-threshold=4" \
  > lab-output/probe-vs-mq/node-b.log 2>&1 &
```

确认两个节点都起来：

```bash
tail -n 20 lab-output/probe-vs-mq/node-a.log
tail -n 20 lab-output/probe-vs-mq/node-b.log
```

再拿到 PID：

```bash
jps -l | grep probe-mq-troubleshooting-demo
```

把两个 PID 记下来，后面 `jcmd / jstack` 都会用到。

## 3. 先看基线

健康检查：

```bash
curl -s http://127.0.0.1:8080/actuator/health/readiness
curl -s http://127.0.0.1:8081/actuator/health/readiness
```

两个节点一开始都应该是 `UP`。

再看节点证据口径：

```bash
curl -s http://127.0.0.1:8080/api/evidence
curl -s http://127.0.0.1:8081/api/evidence
```

重点字段：

- `nodeId`
- `faultEnabled`
- `activeBlockedRequests`
- `mqConsumedCount`
- `recentConsumerThreads`

## 4. 触发 node-b 的 HTTP 阻塞

用 4 个并发请求把 `node-b` 的阻塞 handler 顶到 readiness 阈值：

```bash
seq 1 4 | xargs -I{} -P 4 curl -s \
  "http://127.0.0.1:8081/api/traffic?businessKey=order-{}" \
  > lab-output/probe-vs-mq/node-b-traffic.out
```

这些请求会在 `node-b` 上卡住约 `25s`。

在阻塞窗口里立刻再查：

```bash
curl -s http://127.0.0.1:8081/actuator/health/readiness
curl -s http://127.0.0.1:8081/api/evidence
```

你应该看到：

1. `status` 变成 `DOWN`
2. `activeBlockedRequests` 大于等于 `4`
3. `mqConsumedCount` 还在继续增长

## 5. 模拟 probe-aware 路由

下面这个脚本只会把请求打到 readiness 为 `UP` 的节点：

```bash
for i in $(seq 1 10); do
  if curl -s http://127.0.0.1:8081/actuator/health/readiness | grep -q '"status":"UP"'; then
    curl -s "http://127.0.0.1:8081/api/traffic?businessKey=router-$i" >> lab-output/probe-vs-mq/router.log
  else
    curl -s "http://127.0.0.1:8080/api/traffic?businessKey=router-$i" >> lab-output/probe-vs-mq/router.log
  fi
done
```

阻塞窗口内，这批流量应该基本都落到 `node-a`。

你可以同时对比日志：

```bash
tail -f lab-output/probe-vs-mq/node-a.log
tail -f lab-output/probe-vs-mq/node-b.log
```

观察点：

- `node-a` 继续打印正常 HTTP 请求日志
- `node-b` 基本没有新的业务 HTTP 请求完成日志
- 但 `node-b` 里的 `node-b-mq-consumer-*` 线程还在持续打印消费日志

## 6. 用 jcmd / jstack 留线程证据

先拿 `node-b` 的线程快照：

```bash
NODE_B_PID=$(jps -l | grep probe-mq-troubleshooting-demo | awk 'NR==2 {print $1}')
jcmd "$NODE_B_PID" Thread.print -l > lab-output/probe-vs-mq/node-b-thread-print.txt
jstack "$NODE_B_PID" > lab-output/probe-vs-mq/node-b-jstack.txt
```

重点 grep 这两类线程：

```bash
grep -n "http-nio-8081-exec" lab-output/probe-vs-mq/node-b-thread-print.txt | head
grep -n "node-b-mq-consumer" lab-output/probe-vs-mq/node-b-thread-print.txt | head
```

你要证明的是：

1. `http-nio-8081-exec-*` 线程在阻塞 handler 上停住
2. `node-b-mq-consumer-*` 线程仍在正常轮询和消费

## 7. 现场证据至少留这几份

```bash
curl -s http://127.0.0.1:8081/actuator/health/readiness \
  > lab-output/probe-vs-mq/node-b-readiness.json
curl -s http://127.0.0.1:8081/api/evidence \
  > lab-output/probe-vs-mq/node-b-evidence.json
jcmd "$NODE_B_PID" Thread.print -l \
  > lab-output/probe-vs-mq/node-b-thread-print.txt
jstack "$NODE_B_PID" \
  > lab-output/probe-vs-mq/node-b-jstack.txt
grep "node-b" lab-output/probe-vs-mq/node-b.log | tail -n 100 \
  > lab-output/probe-vs-mq/node-b-tail.txt
```

## 8. 可选补充：GC / 延迟观察

这次场景主线不是 GC，但你可以顺手补一轮 JVM 口径：

```bash
jcmd "$NODE_B_PID" GC.heap_info
jstat -gcutil "$NODE_B_PID" 1000 10
```

如果你想证明这不是 Full GC 或 OOM 导致的全进程失活，而只是 HTTP 侧阻塞，更应该重点看：

- readiness 何时从 `UP` 变成 `DOWN`
- HTTP 线程名和阻塞栈
- MQ consumer 线程是否仍有持续消费

## 9. 收尾

演练结束后停进程：

```bash
kill "$NODE_B_PID"
jps -l | grep probe-mq-troubleshooting-demo | awk 'NR==1 {print $1}' | xargs kill
```

再把证据打包：

```bash
tar -czf lab-output/probe-vs-mq.tar.gz lab-output/probe-vs-mq
```
