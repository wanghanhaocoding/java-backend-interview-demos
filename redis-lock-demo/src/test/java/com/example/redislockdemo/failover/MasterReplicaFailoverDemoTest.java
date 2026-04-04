package com.example.redislockdemo.failover;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MasterReplicaFailoverDemoTest {

    private final MasterReplicaFailoverDemoService masterReplicaFailoverDemoService = new MasterReplicaFailoverDemoService();

    @Test
    void replicationLagCanBreakMutualExclusionAfterFailover() {
        MasterReplicaFailoverDemoService.ReplicationGapDemoResult result =
                masterReplicaFailoverDemoService.replicationLagCausesLockLoss("order-3001");

        assertThat(result.mutualExclusionBroken()).isTrue();
        assertThat(result.oldOwner()).isNotNull();
        assertThat(result.newOwner()).isNotNull();
        assertThat(result.oldOwner().ownerId()).isEqualTo("client-A");
        assertThat(result.newOwner().ownerId()).isEqualTo("client-B");
        assertThat(result.conclusion()).contains("单靠 Redis 主从锁做不到绝对安全");
    }

    @Test
    void fencingTokenRejectsStaleOwnerWrite() {
        MasterReplicaFailoverDemoService.FencingTokenDemoResult result =
                masterReplicaFailoverDemoService.fencingTokenProtectsDownstream("inventory-3001");

        assertThat(result.latestToken()).isGreaterThan(result.staleToken());
        assertThat(result.acceptedWrite().accepted()).isTrue();
        assertThat(result.rejectedWrite().accepted()).isFalse();
        assertThat(result.rejectedWrite().reason()).contains("stale-token-rejected");
        assertThat(result.latestAcceptedFenceToken()).isEqualTo(result.latestToken());
    }
}
