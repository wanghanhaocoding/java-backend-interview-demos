package com.example.distributedtxdemo.demo;

import com.example.distributedtxdemo.common.DemoDataResetService;
import com.example.distributedtxdemo.localtx.LocalTransactionDemoService;
import com.example.distributedtxdemo.outbox.OutboxDemoService;
import com.example.distributedtxdemo.saga.SagaDemoService;
import com.example.distributedtxdemo.state.StateMachineDemoService;
import com.example.distributedtxdemo.tcc.TccDemoService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;

@Component
@ConditionalOnProperty(name = "teaching.runner.enabled", havingValue = "true", matchIfMissing = true)
public class DemoRunner implements CommandLineRunner {

    private final DemoDataResetService demoDataResetService;
    private final LocalTransactionDemoService localTransactionDemoService;
    private final StateMachineDemoService stateMachineDemoService;
    private final OutboxDemoService outboxDemoService;
    private final TccDemoService tccDemoService;
    private final SagaDemoService sagaDemoService;

    public DemoRunner(DemoDataResetService demoDataResetService,
                      LocalTransactionDemoService localTransactionDemoService,
                      StateMachineDemoService stateMachineDemoService,
                      OutboxDemoService outboxDemoService,
                      TccDemoService tccDemoService,
                      SagaDemoService sagaDemoService) {
        this.demoDataResetService = demoDataResetService;
        this.localTransactionDemoService = localTransactionDemoService;
        this.stateMachineDemoService = stateMachineDemoService;
        this.outboxDemoService = outboxDemoService;
        this.tccDemoService = tccDemoService;
        this.sagaDemoService = sagaDemoService;
    }

    @Override
    public void run(String... args) {
        demoDataResetService.resetAll();

        printTitle("1. 本地事务：正常提交");
        localTransactionDemoService.transfer("ALICE", "BOB", BigDecimal.valueOf(100));
        System.out.println("结论 = 单库内转账成功，本地事务保证原子提交");

        demoDataResetService.resetAll();
        printTitle("2. 本地事务：异常回滚");
        try {
            localTransactionDemoService.transferAndFail("ALICE", "BOB", BigDecimal.valueOf(100));
        } catch (Exception ex) {
            System.out.println("触发异常 = " + ex.getMessage());
        }
        System.out.println("结论 = 同一个本地事务内，异常会让账户改动一起回滚");

        demoDataResetService.resetAll();
        printTitle("3. 柔性事务：成功回执先到，迟到处理中回执被忽略");
        StateMachineDemoService.ProjectStyleDistributedTxResult stateResult = stateMachineDemoService.projectStyleFlowDemo(
                "REQ-1001",
                BigDecimal.valueOf(88),
                StateMachineDemoService.InstructionStatus.SUCCESS,
                true,
                false,
                Duration.ofMinutes(10),
                Duration.ofMinutes(5)
        );
        stateResult.timeline().forEach(System.out::println);
        System.out.println("finalStatus = " + stateResult.finalStatus());

        demoDataResetService.resetAll();
        printTitle("4. 柔性事务：超时补偿 / 对账扫描");
        StateMachineDemoService.ProjectStyleDistributedTxResult reconcileResult = stateMachineDemoService.projectStyleFlowDemo(
                "REQ-1002",
                BigDecimal.valueOf(66),
                StateMachineDemoService.InstructionStatus.PROCESSING,
                false,
                true,
                Duration.ofMinutes(10),
                Duration.ofMinutes(5)
        );
        reconcileResult.timeline().forEach(System.out::println);

        demoDataResetService.resetAll();
        printTitle("5. Outbox：业务成功 + 本地消息表 + 重试投递 + 消费幂等");
        OutboxDemoService.OutboxFlowResult outboxResult = outboxDemoService.endToEndDemo("ORD-1001", BigDecimal.valueOf(128));
        outboxResult.steps().forEach(System.out::println);
        System.out.println("finalEventStatus = " + outboxResult.finalEventStatus());

        demoDataResetService.resetAll();
        printTitle("6. TCC：Try + Confirm");
        TccDemoService.TccFlowResult tccConfirm = tccDemoService.successfulConfirmDemo("TCC-1001", BigDecimal.valueOf(120));
        tccConfirm.steps().forEach(System.out::println);
        System.out.println("finalReservationStatus = " + tccConfirm.finalReservationStatus());

        demoDataResetService.resetAll();
        printTitle("7. TCC：空回滚 + 防悬挂 + 正常 Cancel");
        TccDemoService.TccFlowResult tccCancel = tccDemoService.cancelAndEmptyCancelDemo("TCC-EMPTY-01", "TCC-1002", BigDecimal.valueOf(90));
        tccCancel.steps().forEach(System.out::println);
        System.out.println("finalReservationStatus = " + tccCancel.finalReservationStatus());

        printTitle("8. Saga：后置步骤失败后执行补偿");
        SagaDemoService.SagaResult sagaResult = sagaDemoService.orchestrateWithCompensation("SAGA-1001", true);
        sagaResult.steps().forEach(System.out::println);
        sagaResult.compensations().forEach(step -> System.out.println("compensation -> " + step));
        System.out.println("success = " + sagaResult.success());
    }

    private void printTitle(String title) {
        System.out.println();
        System.out.println("=== " + title + " ===");
    }
}
