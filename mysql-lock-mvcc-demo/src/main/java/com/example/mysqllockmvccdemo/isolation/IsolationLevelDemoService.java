package com.example.mysqllockmvccdemo.isolation;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class IsolationLevelDemoService {

    public NonRepeatableReadResult nonRepeatableReadDemo() {
        List<String> steps = new ArrayList<>();

        int committedValue = 100;
        int rcFirstRead = committedValue;
        int rrSnapshotRead = committedValue;
        steps.add("1. 事务 T1 开始时，账户余额的已提交值是 100");

        committedValue = 120;
        steps.add("2. 事务 T2 把余额更新成 120 并提交");

        int rcSecondRead = committedValue;
        int rrSecondRead = rrSnapshotRead;
        steps.add("3. READ COMMITTED 每次读都重新取最新已提交值，所以第二次读到 120");
        steps.add("4. REPEATABLE READ 保持第一次快照，因此同一事务内第二次仍然看到 100");

        return new NonRepeatableReadResult(
                steps,
                rcFirstRead,
                rcSecondRead,
                rrSnapshotRead,
                rrSecondRead
        );
    }

    public PhantomReadResult phantomReadDemo() {
        List<String> steps = new ArrayList<>();

        int matchingRowsAtStart = 2;
        int matchingRowsAfterInsert = 3;
        steps.add("1. T1 第一次范围查询命中 2 行记录");
        steps.add("2. T2 插入一行新的匹配记录并提交");
        steps.add("3. 如果没有范围锁或快照保护，T1 第二次范围查询可能看到第 3 行");

        return new PhantomReadResult(steps, matchingRowsAtStart, matchingRowsAfterInsert);
    }

    public static final class NonRepeatableReadResult {

        private final List<String> steps;
        private final int readCommittedFirstRead;
        private final int readCommittedSecondRead;
        private final int repeatableReadFirstRead;
        private final int repeatableReadSecondRead;

        public NonRepeatableReadResult(List<String> steps,
                                       int readCommittedFirstRead,
                                       int readCommittedSecondRead,
                                       int repeatableReadFirstRead,
                                       int repeatableReadSecondRead) {
            this.steps = steps;
            this.readCommittedFirstRead = readCommittedFirstRead;
            this.readCommittedSecondRead = readCommittedSecondRead;
            this.repeatableReadFirstRead = repeatableReadFirstRead;
            this.repeatableReadSecondRead = repeatableReadSecondRead;
        }

        public List<String> steps() {
            return steps;
        }

        public int readCommittedFirstRead() {
            return readCommittedFirstRead;
        }

        public int readCommittedSecondRead() {
            return readCommittedSecondRead;
        }

        public int repeatableReadFirstRead() {
            return repeatableReadFirstRead;
        }

        public int repeatableReadSecondRead() {
            return repeatableReadSecondRead;
        }
    }

    public static final class PhantomReadResult {

        private final List<String> steps;
        private final int initialMatchingRows;
        private final int matchingRowsAfterInsert;

        public PhantomReadResult(List<String> steps, int initialMatchingRows, int matchingRowsAfterInsert) {
            this.steps = steps;
            this.initialMatchingRows = initialMatchingRows;
            this.matchingRowsAfterInsert = matchingRowsAfterInsert;
        }

        public List<String> steps() {
            return steps;
        }

        public int initialMatchingRows() {
            return initialMatchingRows;
        }

        public int matchingRowsAfterInsert() {
            return matchingRowsAfterInsert;
        }
    }
}
