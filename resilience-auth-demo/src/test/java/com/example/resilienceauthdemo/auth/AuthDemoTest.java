package com.example.resilienceauthdemo.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "teaching.runner.enabled=false")
class AuthDemoTest {

    @Autowired
    private AuthDemoService authDemoService;

    @Test
    void jwtShouldBeValidBeforeBlacklistAndInvalidAfterBlacklist() {
        String token = authDemoService.issueJwt("alice", Set.of("ORDER_ADMIN"), Instant.now().plusSeconds(300).getEpochSecond());

        AuthDemoService.TokenClaims claims = authDemoService.verifyJwt(token);
        assertThat(claims.subject()).isEqualTo("alice");
        assertThat(claims.roles()).contains("ORDER_ADMIN");
        assertThat(authDemoService.isTokenValid(token)).isTrue();

        authDemoService.blacklistToken(token);
        assertThat(authDemoService.isTokenValid(token)).isFalse();
    }

    @Test
    void rbacShouldGrantPermissionsByRole() {
        boolean canWriteOrder = authDemoService.hasPermission(Set.of("ORDER_ADMIN"), "ORDER_WRITE");
        boolean canRefund = authDemoService.hasPermission(Set.of("OPS"), "PAYMENT_REFUND");

        assertThat(canWriteOrder).isTrue();
        assertThat(canRefund).isFalse();
    }

    @Test
    void loginFlowShouldDemonstrateSessionJwtAndBlacklist() {
        AuthDemoService.LoginResult result =
                authDemoService.loginAndAuthorizeDemo("alice", Set.of("OPS", "ORDER_ADMIN"));

        assertThat(result.sessionId()).startsWith("SESSION-alice-");
        assertThat(result.canWriteOrder()).isTrue();
        assertThat(result.validAfterBlacklist()).isFalse();
    }
}
