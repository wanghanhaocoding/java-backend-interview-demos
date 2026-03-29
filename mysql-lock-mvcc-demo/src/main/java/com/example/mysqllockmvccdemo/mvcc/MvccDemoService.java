package com.example.mysqllockmvccdemo.mvcc;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class MvccDemoService {

    public MvccSnapshotResult versionChainDemo() {
        List<RowVersion> versionChain = List.of(
                new RowVersion(1, 100, 1),
                new RowVersion(2, 120, 2)
        );
        List<String> steps = new ArrayList<>();

        int transaction1ReadView = 1;
        int snapshotReadValue = visibleValue(versionChain, transaction1ReadView);
        int currentReadValue = versionChain.get(versionChain.size() - 1).balance();

        steps.add("1. 同一行保留两个版本：commitId=1 时余额 100，commitId=2 时余额 120");
        steps.add("2. T1 的 read view 停留在 commitId=1，因此快照读看到 100");
        steps.add("3. 当前读会直接看最新版本，并且通常伴随锁，所以当前读看到 120");

        return new MvccSnapshotResult(steps, snapshotReadValue, currentReadValue, versionChain.size());
    }

    private int visibleValue(List<RowVersion> versionChain, int readViewCommitId) {
        for (int i = versionChain.size() - 1; i >= 0; i--) {
            RowVersion version = versionChain.get(i);
            if (version.commitId() <= readViewCommitId) {
                return version.balance();
            }
        }
        throw new IllegalStateException("No visible version found");
    }

    public record MvccSnapshotResult(
            List<String> steps,
            int snapshotReadValue,
            int currentReadValue,
            int versionCount
    ) {
    }

    private record RowVersion(
            int versionId,
            int balance,
            int commitId
    ) {
    }
}
