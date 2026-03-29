package com.example.asyncjobcentercoredemo.core;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class AsyncJobCenterCoreDemoService {

    private final AtomicInteger sequence = new AtomicInteger();

    public CoreFlowResult serverWorkerLifecycleDemo() {
        sequence.set(0);
        List<String> steps = new ArrayList<>();
        List<String> statusTimeline = new ArrayList<>();
        List<String> stageTimeline = new ArrayList<>();

        Map<String, TaskScheduleCfg> scheduleCfgTable = new LinkedHashMap<>();
        scheduleCfgTable.put("treasury_receipt", new TaskScheduleCfg("treasury_receipt", 10, 3, 60));

        Map<String, TaskPos> taskPosTable = new LinkedHashMap<>();
        taskPosTable.put("treasury_receipt", new TaskPos("treasury_receipt", 0, 0));

        Map<String, List<AsyncTask>> taskTables = new LinkedHashMap<>();
        String tableName = getTableName("treasury_receipt", 0);
        taskTables.put(tableName, new ArrayList<>());

        steps.add("1. 初始化 task_schedule_cfg：taskType=treasury_receipt, scheduleLimit=10, retry=3");
        steps.add("2. 初始化 task_pos：beginPos=0, endPos=0，对应表 " + tableName);

        AsyncTask createdTask = createTask("treasury_receipt", "prepareReceipt", taskPosTable, taskTables);
        statusTimeline.add(createdTask.status().name());
        stageTimeline.add(createdTask.stage());
        steps.add("3. server 调用 create，把任务 " + createdTask.taskId() + " 落到 " + tableName);

        AsyncTask heldTask = holdTask("treasury_receipt", taskPosTable, taskTables);
        statusTimeline.add(heldTask.status().name());
        stageTimeline.add(heldTask.stage());
        steps.add("4. worker 调用 hold，把任务占有为 PROCESSING，准备执行阶段 " + heldTask.stage());

        AsyncTask afterFirstStage = advanceStage(heldTask, "dispatchReceipt", TaskStatus.PENDING);
        saveTask(afterFirstStage, tableName, taskTables);
        statusTimeline.add(afterFirstStage.status().name());
        stageTimeline.add(afterFirstStage.stage());
        steps.add("5. 第一阶段执行完成，set 回下一阶段 dispatchReceipt，并把状态回写成 PENDING");

        AsyncTask secondHeldTask = holdTask("treasury_receipt", taskPosTable, taskTables);
        statusTimeline.add(secondHeldTask.status().name());
        stageTimeline.add(secondHeldTask.stage());
        steps.add("6. worker 再次 hold，同一任务进入第二阶段 dispatchReceipt");

        AsyncTask completedTask = advanceStage(secondHeldTask, "finished", TaskStatus.SUCCESS);
        saveTask(completedTask, tableName, taskTables);
        statusTimeline.add(completedTask.status().name());
        stageTimeline.add(completedTask.stage());
        steps.add("7. 第二阶段执行完成，set 回终态 SUCCESS，任务生命周期闭环");

        Map<String, List<String>> tableTasks = new LinkedHashMap<>();
        taskTables.forEach((name, tasks) -> tableTasks.put(
                name,
                tasks.stream().map(AsyncTask::taskId).toList()
        ));

        return new CoreFlowResult(steps, statusTimeline, stageTimeline, tableTasks);
    }

    private AsyncTask createTask(String taskType,
                                 String stage,
                                 Map<String, TaskPos> taskPosTable,
                                 Map<String, List<AsyncTask>> taskTables) {
        TaskPos taskPos = taskPosTable.get(taskType);
        String taskId = String.format("AJOB-%04d_%s_%d",
                sequence.incrementAndGet(),
                taskType.replace("_", "-"),
                taskPos.endPos());
        AsyncTask task = new AsyncTask(taskId, taskType, stage, TaskStatus.PENDING, System.currentTimeMillis() / 1000);
        taskTables.get(getTableName(taskType, taskPos.endPos())).add(task);
        return task;
    }

    private AsyncTask holdTask(String taskType,
                               Map<String, TaskPos> taskPosTable,
                               Map<String, List<AsyncTask>> taskTables) {
        TaskPos taskPos = taskPosTable.get(taskType);
        String tableName = getTableName(taskType, taskPos.beginPos());
        List<AsyncTask> tasks = taskTables.get(tableName);
        for (int index = 0; index < tasks.size(); index++) {
            AsyncTask task = tasks.get(index);
            if (task.status() == TaskStatus.PENDING) {
                AsyncTask processingTask = new AsyncTask(task.taskId(), task.taskType(), task.stage(),
                        TaskStatus.PROCESSING, task.orderTime());
                tasks.set(index, processingTask);
                return processingTask;
            }
        }
        throw new IllegalStateException("No pending task found");
    }

    private AsyncTask advanceStage(AsyncTask task, String nextStage, TaskStatus nextStatus) {
        return new AsyncTask(task.taskId(), task.taskType(), nextStage, nextStatus, task.orderTime());
    }

    private void saveTask(AsyncTask updatedTask,
                          String tableName,
                          Map<String, List<AsyncTask>> taskTables) {
        List<AsyncTask> tasks = taskTables.get(tableName);
        for (int index = 0; index < tasks.size(); index++) {
            if (tasks.get(index).taskId().equals(updatedTask.taskId())) {
                tasks.set(index, updatedTask);
                return;
            }
        }
        throw new IllegalStateException("Task not found: " + updatedTask.taskId());
    }

    private String getTableName(String taskType, long pos) {
        return String.format("t_%s_task_%d", taskType, pos);
    }

    private record TaskScheduleCfg(String taskType, int scheduleLimit, int maxRetryNum, int maxRetryIntervalSeconds) {
    }

    private record TaskPos(String taskType, int beginPos, int endPos) {
    }

    private record AsyncTask(String taskId, String taskType, String stage, TaskStatus status, long orderTime) {
    }

    private enum TaskStatus {
        PENDING,
        PROCESSING,
        SUCCESS
    }

    public record CoreFlowResult(
            List<String> steps,
            List<String> statusTimeline,
            List<String> stageTimeline,
            Map<String, List<String>> tableTasks
    ) {
    }
}
