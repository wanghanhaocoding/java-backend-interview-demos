package com.example.jvmstabilitydemo.oom;

/**
 * 更贴近项目语境的 OOM 教学入口。
 *
 * <p>这里不再是“单纯往 List 里塞 byte[]”，而是模拟 ScheduleCenter 中：
 * 任务触发失败 -> 本地补偿快照堆积 -> 逐步打满堆内存 的真实链路。</p>
 */
public class OomLeakDemo {

    public static void main(String[] args) {
        if (args.length == 0) {
            printInstruction();
            return;
        }

        ScheduleCenterTaskStormSimulator simulator = new ScheduleCenterTaskStormSimulator(new LeakyScheduleSnapshotBuffer());
        switch (args[0]) {
            case "--preview" -> System.out.println(simulator.previewOneRound());
            case "--run" -> simulator.runUntilOom();
            default -> printInstruction();
        }
    }

    public static ScheduleCenterTaskStormSimulator.SimulationRoundResult previewRound() {
        ScheduleCenterTaskStormSimulator simulator = new ScheduleCenterTaskStormSimulator(new LeakyScheduleSnapshotBuffer());
        return simulator.previewOneRound();
    }

    private static void printInstruction() {
        System.out.println("OOM 教学示例不会默认执行，以免误伤当前机器。");
        System.out.println("你可以先看更贴近项目的预览模式：");
        System.out.println("java -cp target/classes com.example.jvmstabilitydemo.oom.OomLeakDemo --preview");
        System.out.println();
        System.out.println("如果你要真的观察 OOM，请先编译：mvn -q -DskipTests compile");
        System.out.println("再执行：java -Xms128m -Xmx128m -cp target/classes com.example.jvmstabilitydemo.oom.OomLeakDemo --run");
    }
}
