package com.example.redislockdemo.failover;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class MasterReplicaFailoverDemoService {

    public ReplicationGapDemoResult replicationLagCausesLockLoss(String businessKey) {
        String lockKey = "lock:failover:" + businessKey;
        SimulatedRedisNode oldMaster = new SimulatedRedisNode("redis-master-1", "master");
        SimulatedRedisNode replica = new SimulatedRedisNode("redis-replica-1", "replica");
        List<String> steps = new ArrayList<String>();

        LockOwnership oldOwner = oldMaster.tryAcquire(lockKey, "client-A");
        steps.add("client-A 先在 old master 拿锁成功，ownerToken=" + oldOwner.ownerToken());
        steps.add("Redis 主从复制默认是异步的，此时 replica 还没收到这把锁。");
        steps.add("old master 宕机后，原 replica 被提升为 new master。");

        SimulatedRedisNode promotedMaster = replica.promoteToMaster();
        LockOwnership newOwner = promotedMaster.tryAcquire(lockKey, "client-B");
        steps.add("client-B 在 promoted master 又拿到同一把锁，ownerToken=" + newOwner.ownerToken());

        boolean mutualExclusionBroken = oldOwner != null && newOwner != null;
        String conclusion = mutualExclusionBroken
                ? "结论 = 单靠 Redis 主从锁做不到绝对安全。复制丢失后，两个客户端都可能认为自己持有锁。"
                : "结论 = 本次场景没有复现出双持锁，但根因风险依然存在。";

        return new ReplicationGapDemoResult(
                lockKey,
                oldMaster.nodeName(),
                promotedMaster.nodeName(),
                oldOwner,
                newOwner,
                mutualExclusionBroken,
                immutableListCopy(steps),
                conclusion
        );
    }

    public FencingTokenDemoResult fencingTokenProtectsDownstream(String resourceName) {
        FenceTokenIssuer issuer = new FenceTokenIssuer(100);
        FencedBusinessResource resource = new FencedBusinessResource(resourceName);
        List<String> steps = new ArrayList<String>();
        List<String> guardrails = new ArrayList<String>();

        long staleToken = issuer.nextToken();
        steps.add("client-A 先拿到旧锁，并从线性一致的序号源申请 fencing token=" + staleToken);

        long latestToken = issuer.nextToken();
        steps.add("主从切换后 client-B 又拿到新锁，并拿到更大的 fencing token=" + latestToken);

        BusinessWriteOutcome acceptedWrite = resource.apply("client-B", latestToken, "inventory=18");
        steps.add("下游资源先处理 client-B，accepted=" + acceptedWrite.accepted());

        BusinessWriteOutcome rejectedWrite = resource.apply("client-A", staleToken, "inventory=17");
        steps.add("旧 owner client-A 迟到写入时，因为 token 更小，被下游资源拒绝。");

        guardrails.add("核心写操作要做 version/CAS 或 fencing token 比较，不能只相信 Redis 锁。");
        guardrails.add("订单、流水、回调这类核心链路仍要做幂等表、唯一索引或状态机前进校验。");
        guardrails.add("如果下游资源完全不校验 token，再好的 Redis 锁也兜不住主从切换后的双持锁。");

        return new FencingTokenDemoResult(
                resourceName,
                staleToken,
                latestToken,
                acceptedWrite,
                rejectedWrite,
                resource.latestAcceptedFenceToken(),
                immutableListCopy(steps),
                immutableListCopy(guardrails)
        );
    }

    private <T> List<T> immutableListCopy(List<T> source) {
        return Collections.unmodifiableList(new ArrayList<T>(source));
    }

    private static final class SimulatedRedisNode {
        private static final AtomicLong OWNER_TOKEN_SEQUENCE = new AtomicLong(1000);

        private final String nodeName;
        private String role;
        private final Map<String, LockOwnership> lockByKey = new LinkedHashMap<String, LockOwnership>();

        private SimulatedRedisNode(String nodeName, String role) {
            this.nodeName = nodeName;
            this.role = role;
        }

        private LockOwnership tryAcquire(String lockKey, String ownerId) {
            if (lockByKey.containsKey(lockKey)) {
                return null;
            }
            LockOwnership ownership = new LockOwnership(
                    ownerId,
                    ownerId + "-token-" + OWNER_TOKEN_SEQUENCE.incrementAndGet()
            );
            lockByKey.put(lockKey, ownership);
            return ownership;
        }

        private SimulatedRedisNode promoteToMaster() {
            this.role = "master";
            return this;
        }

        private String nodeName() {
            return nodeName + "(" + role + ")";
        }
    }

    private static final class FenceTokenIssuer {
        private final AtomicLong sequence;

        private FenceTokenIssuer(long initialValue) {
            this.sequence = new AtomicLong(initialValue);
        }

        private long nextToken() {
            return sequence.incrementAndGet();
        }
    }

    private static final class FencedBusinessResource {
        private final String resourceName;
        private long latestAcceptedFenceToken;

        private FencedBusinessResource(String resourceName) {
            this.resourceName = resourceName;
        }

        private synchronized BusinessWriteOutcome apply(String ownerId, long fenceToken, String newValue) {
            if (fenceToken <= latestAcceptedFenceToken) {
                return BusinessWriteOutcome.rejected(resourceName, ownerId, fenceToken, newValue, latestAcceptedFenceToken);
            }
            latestAcceptedFenceToken = fenceToken;
            return BusinessWriteOutcome.accepted(resourceName, ownerId, fenceToken, newValue);
        }

        private long latestAcceptedFenceToken() {
            return latestAcceptedFenceToken;
        }
    }

    public static final class ReplicationGapDemoResult {
        private final String lockKey;
        private final String oldMasterNode;
        private final String promotedMasterNode;
        private final LockOwnership oldOwner;
        private final LockOwnership newOwner;
        private final boolean mutualExclusionBroken;
        private final List<String> steps;
        private final String conclusion;

        public ReplicationGapDemoResult(String lockKey,
                                        String oldMasterNode,
                                        String promotedMasterNode,
                                        LockOwnership oldOwner,
                                        LockOwnership newOwner,
                                        boolean mutualExclusionBroken,
                                        List<String> steps,
                                        String conclusion) {
            this.lockKey = lockKey;
            this.oldMasterNode = oldMasterNode;
            this.promotedMasterNode = promotedMasterNode;
            this.oldOwner = oldOwner;
            this.newOwner = newOwner;
            this.mutualExclusionBroken = mutualExclusionBroken;
            this.steps = steps;
            this.conclusion = conclusion;
        }

        public String lockKey() {
            return lockKey;
        }

        public String oldMasterNode() {
            return oldMasterNode;
        }

        public String promotedMasterNode() {
            return promotedMasterNode;
        }

        public LockOwnership oldOwner() {
            return oldOwner;
        }

        public LockOwnership newOwner() {
            return newOwner;
        }

        public boolean mutualExclusionBroken() {
            return mutualExclusionBroken;
        }

        public List<String> steps() {
            return steps;
        }

        public String conclusion() {
            return conclusion;
        }
    }

    public static final class FencingTokenDemoResult {
        private final String resourceName;
        private final long staleToken;
        private final long latestToken;
        private final BusinessWriteOutcome acceptedWrite;
        private final BusinessWriteOutcome rejectedWrite;
        private final long latestAcceptedFenceToken;
        private final List<String> steps;
        private final List<String> guardrails;

        public FencingTokenDemoResult(String resourceName,
                                      long staleToken,
                                      long latestToken,
                                      BusinessWriteOutcome acceptedWrite,
                                      BusinessWriteOutcome rejectedWrite,
                                      long latestAcceptedFenceToken,
                                      List<String> steps,
                                      List<String> guardrails) {
            this.resourceName = resourceName;
            this.staleToken = staleToken;
            this.latestToken = latestToken;
            this.acceptedWrite = acceptedWrite;
            this.rejectedWrite = rejectedWrite;
            this.latestAcceptedFenceToken = latestAcceptedFenceToken;
            this.steps = steps;
            this.guardrails = guardrails;
        }

        public String resourceName() {
            return resourceName;
        }

        public long staleToken() {
            return staleToken;
        }

        public long latestToken() {
            return latestToken;
        }

        public BusinessWriteOutcome acceptedWrite() {
            return acceptedWrite;
        }

        public BusinessWriteOutcome rejectedWrite() {
            return rejectedWrite;
        }

        public long latestAcceptedFenceToken() {
            return latestAcceptedFenceToken;
        }

        public List<String> steps() {
            return steps;
        }

        public List<String> guardrails() {
            return guardrails;
        }
    }

    public static final class LockOwnership {
        private final String ownerId;
        private final String ownerToken;

        public LockOwnership(String ownerId, String ownerToken) {
            this.ownerId = ownerId;
            this.ownerToken = ownerToken;
        }

        public String ownerId() {
            return ownerId;
        }

        public String ownerToken() {
            return ownerToken;
        }
    }

    public static final class BusinessWriteOutcome {
        private final String resourceName;
        private final String ownerId;
        private final long fenceToken;
        private final String newValue;
        private final boolean accepted;
        private final String reason;

        private BusinessWriteOutcome(String resourceName,
                                     String ownerId,
                                     long fenceToken,
                                     String newValue,
                                     boolean accepted,
                                     String reason) {
            this.resourceName = resourceName;
            this.ownerId = ownerId;
            this.fenceToken = fenceToken;
            this.newValue = newValue;
            this.accepted = accepted;
            this.reason = reason;
        }

        public static BusinessWriteOutcome accepted(String resourceName,
                                                    String ownerId,
                                                    long fenceToken,
                                                    String newValue) {
            return new BusinessWriteOutcome(resourceName, ownerId, fenceToken, newValue, true, "accepted");
        }

        public static BusinessWriteOutcome rejected(String resourceName,
                                                    String ownerId,
                                                    long fenceToken,
                                                    String newValue,
                                                    long latestAcceptedFenceToken) {
            return new BusinessWriteOutcome(
                    resourceName,
                    ownerId,
                    fenceToken,
                    newValue,
                    false,
                    "stale-token-rejected(latestAccepted=" + latestAcceptedFenceToken + ")"
            );
        }

        public String resourceName() {
            return resourceName;
        }

        public String ownerId() {
            return ownerId;
        }

        public long fenceToken() {
            return fenceToken;
        }

        public String newValue() {
            return newValue;
        }

        public boolean accepted() {
            return accepted;
        }

        public String reason() {
            return reason;
        }
    }
}
