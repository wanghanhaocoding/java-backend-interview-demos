package com.example.jvmstabilitydemo.oom;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 故意保留“真实项目里容易出问题的味道”：
 * 既维护顺序列表，又维护按 jobId 的索引，还没有任何上限和淘汰策略。
 */
public class LeakyLocalRetrySnapshotBuffer {

    private final List<JobCallbackSnapshot> orderedSnapshots = new ArrayList<>();
    private final Map<String, JobCallbackSnapshot> latestByJobId = new HashMap<>();
    private final Map<String, List<JobCallbackSnapshot>> snapshotsByBizType = new HashMap<>();

    public void store(JobCallbackSnapshot snapshot) {
        orderedSnapshots.add(snapshot);
        latestByJobId.put(snapshot.jobId(), snapshot);
        snapshotsByBizType
            .computeIfAbsent(snapshot.bizType(), key -> new ArrayList<>())
            .add(snapshot);
    }

    public int snapshotCount() {
        return orderedSnapshots.size();
    }

    public long retainedPayloadBytes() {
        long total = 0;
        for (JobCallbackSnapshot snapshot : orderedSnapshots) {
            total += snapshot.taskPayload().length;
            total += snapshot.responseBody().length;
        }
        return total;
    }

    public String latestIncidentHint() {
        return "bufferHasNoLimitAndNoEviction";
    }
}
