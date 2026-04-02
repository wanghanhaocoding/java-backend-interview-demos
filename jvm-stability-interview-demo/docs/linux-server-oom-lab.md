# Linux 服务器实操指南：第 1 步 OOM

这份文档只做一件事：

在一台空 Linux 云服务器上，把 `OOM` 场景从编译、运行、观察到面试表达完整走一遍。

## 0. 适用前提

- 服务器系统：`Linux`
- JDK：`8`
- Maven：可选
- 当前目录：`jvm-stability-interview-demo`

这个模块已经改成 `JDK8` 可运行。

如果你的服务器已经有 `JDK8`，可以直接跳到第 1 步，不用再装新 JDK。

Maven 不是硬要求。

- 如果你本机 Maven 能用，脚本会先走 Maven 编译
- 如果 Maven 没装或者太老，脚本会自动回退到 `javac`

### Ubuntu / Debian

```bash
sudo apt update
sudo apt install -y openjdk-8-jdk maven procps
```

### CentOS / RHEL / openEuler / Anolis

```bash
sudo yum install -y java-1.8.0-openjdk-devel maven procps-ng
```

## 1. 上传代码后先确认环境

先执行：

```bash
java -version
mvn -version
```

你应该至少确认两件事：

- `java` 是 `1.8`
- `javac` 可以正常执行

## 2. 先做安全预览

先不要直接把进程跑爆，先看一轮预览输出。

```bash
chmod +x scripts/run-oom-lab.sh
./scripts/run-oom-lab.sh preview
```

这一步只会打印一轮模拟结果，不会真的把堆打满。

## 3. 再执行真实 OOM

确认预览没问题后，再执行：

```bash
./scripts/run-oom-lab.sh run
```

这个脚本会自动做这几件事：

1. 编译项目
2. 创建 `lab-output/oom`
3. 用小堆启动 `OomLeakDemo`
4. 打开 GC 日志
5. 在 OOM 时自动生成 heap dump

如果你的 `mvn` 太老，比如 `3.0.x`，脚本会自动降级成 `javac` 编译，不需要你手工处理。

## 4. 用第二个终端观察现场

当第一个终端开始持续打印日志后，开第二个终端执行：

```bash
jps -l
```

拿到 `com.example.jvmstabilitydemo.oom.OomLeakDemo` 的 `pid` 后，再执行：

```bash
jstat -gcutil <pid> 1000 20
jcmd <pid> GC.heap_info
```

你重点看这几个信号：

- `Old` 区越来越高
- `FGC` 次数在增加
- Full GC 后占用回落不明显

## 5. OOM 发生后看落盘证据

脚本默认把产物放在：

```bash
lab-output/oom
```

你重点看：

```bash
ls -lh lab-output/oom
```

正常会看到：

- `gc.log`
- 一个或多个 `.hprof` 文件

如果你想先快速验证：

```bash
tail -n 50 lab-output/oom/gc.log
```

## 6. 这个场景你要得出的结论

这一步你不是只为了看到 `OutOfMemoryError`，而是要把下面这条链路讲顺：

1. 下游波动导致失败任务变多
2. 本地 fallback 快照不断积压
3. 快照对象被强引用长期持有
4. Full GC 也回收不干净
5. 最后堆被打满，触发 OOM

## 7. 面试里的 1 分钟版本

你可以按这个顺序说：

> 我做过一次比较典型的调度中心 OOM 演练，场景是下游波动导致失败任务暴涨，trigger 节点为了补偿把失败任务快照放进本地 fallback buffer。问题在于这个 buffer 没有上限，还维护了多份索引，导致对象被长期强引用。排查时我先看 GC 指标，发现 old 区持续升高、Full GC 后回落有限；再结合日志确认 fallback snapshot 在快速堆积；最后通过 heap dump 进一步确认是本地缓冲持有了大量快照对象。处理上先扩容和限流止血，再把无上限本地缓存改成轻量落库和有限窗口缓存。 

## 8. 这一轮结束后，你应该能回答什么

至少能稳定回答这 4 个问题：

1. 为什么这不是“堆太小”那么简单
2. 为什么 Full GC 后还是降不下来
3. 你是怎么把业务现象和 JVM 现象挂起来的
4. 你先止血做了什么，根因修复又做了什么

下一步就进入 `Full GC` 实操，继续用这个模块里的 `FullGcPressureDemo`。
