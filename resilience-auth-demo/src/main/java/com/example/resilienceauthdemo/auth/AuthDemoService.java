package com.example.resilienceauthdemo.auth;

import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AuthDemoService {

    private static final String SECRET = "demo-secret-for-jwt-signature";

    private final Map<String, SessionRecord> sessionStore = new LinkedHashMap<>();
    private final Set<String> tokenBlacklist = new LinkedHashSet<>();
    private final Map<String, Set<String>> rolePermissions = Map.of(
            "OPS", Set.of("ORDER_READ", "AUDIT_READ"),
            "ORDER_ADMIN", Set.of("ORDER_READ", "ORDER_WRITE", "ORDER_CANCEL"),
            "FINANCE", Set.of("PAYMENT_REFUND", "AUDIT_READ")
    );

    public void reset() {
        sessionStore.clear();
        tokenBlacklist.clear();
    }

    public LoginResult loginAndAuthorizeDemo(String userId, Set<String> roles) {
        reset();
        List<String> steps = new ArrayList<>();

        String sessionId = issueSession(userId);
        steps.add("1. 用户登录成功，服务端创建 session " + sessionId);

        String token = issueJwt(userId, roles, Instant.now().plusSeconds(600).getEpochSecond());
        steps.add("2. 同时签发一个 JWT 风格 token，便于无状态校验");

        TokenClaims claims = verifyJwt(token);
        steps.add("3. token 校验通过，subject=" + claims.subject());

        boolean canWriteOrder = hasPermission(roles, "ORDER_WRITE");
        steps.add("4. RBAC 校验 ORDER_WRITE 权限结果 = " + canWriteOrder);

        blacklistToken(token);
        steps.add("5. 管理员把 token 拉黑，后续校验将失败");

        boolean validAfterBlacklist = isTokenValid(token);
        steps.add("6. 拉黑后再次校验 token，结果 = " + validAfterBlacklist);

        return new LoginResult(steps, sessionId, token, canWriteOrder, validAfterBlacklist);
    }

    public String issueSession(String userId) {
        String sessionId = "SESSION-" + userId + "-" + (sessionStore.size() + 1);
        sessionStore.put(sessionId, new SessionRecord(userId));
        return sessionId;
    }

    public String issueJwt(String userId, Set<String> roles, long expiresAtEpochSecond) {
        String header = base64Url("alg=HS256;typ=JWT");
        String payload = base64Url("sub=" + userId + ";roles=" + String.join(",", roles) + ";exp=" + expiresAtEpochSecond);
        return header + "." + payload + "." + sign(header + "." + payload);
    }

    public TokenClaims verifyJwt(String token) {
        if (!isTokenValid(token)) {
            throw new IllegalStateException("Token is invalid");
        }

        String[] parts = token.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);

        Map<String, String> parsed = new LinkedHashMap<>();
        for (String pair : payload.split(";")) {
            String[] kv = pair.split("=", 2);
            parsed.put(kv[0], kv[1]);
        }

        Set<String> roles = Set.of(parsed.get("roles").split(","));
        return new TokenClaims(parsed.get("sub"), roles, Long.parseLong(parsed.get("exp")));
    }

    public boolean hasPermission(Set<String> roles, String permission) {
        return roles.stream()
                .flatMap(role -> rolePermissions.getOrDefault(role, Set.of()).stream())
                .anyMatch(permission::equals);
    }

    public void blacklistToken(String token) {
        tokenBlacklist.add(token);
    }

    public boolean isTokenValid(String token) {
        if (tokenBlacklist.contains(token)) {
            return false;
        }

        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            return false;
        }

        String body = parts[0] + "." + parts[1];
        if (!sign(body).equals(parts[2])) {
            return false;
        }

        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        long expiresAt = 0L;
        for (String pair : payload.split(";")) {
            if (pair.startsWith("exp=")) {
                expiresAt = Long.parseLong(pair.substring(4));
            }
        }
        return Instant.now().getEpochSecond() < expiresAt;
    }

    private String base64Url(String plainText) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(plainText.getBytes(StandardCharsets.UTF_8));
    }

    private String sign(String content) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] signature = mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to sign JWT demo token", ex);
        }
    }

    public record LoginResult(
            List<String> steps,
            String sessionId,
            String token,
            boolean canWriteOrder,
            boolean validAfterBlacklist
    ) {
    }

    public record TokenClaims(
            String subject,
            Set<String> roles,
            long expiresAtEpochSecond
    ) {
    }

    private record SessionRecord(String userId) {
    }
}
