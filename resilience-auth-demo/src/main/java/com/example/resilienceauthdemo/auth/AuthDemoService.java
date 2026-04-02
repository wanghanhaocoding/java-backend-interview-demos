package com.example.resilienceauthdemo.auth;

import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
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
    private final Map<String, Set<String>> rolePermissions = createRolePermissions();

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

        Set<String> roles = parseRoles(parsed.get("roles"));
        return new TokenClaims(parsed.get("sub"), roles, Long.parseLong(parsed.get("exp")));
    }

    public boolean hasPermission(Set<String> roles, String permission) {
        return roles.stream()
                .flatMap(role -> rolePermissions.getOrDefault(role, Collections.<String>emptySet()).stream())
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

    private Map<String, Set<String>> createRolePermissions() {
        Map<String, Set<String>> permissions = new LinkedHashMap<>();
        permissions.put("OPS", linkedSetOf("ORDER_READ", "AUDIT_READ"));
        permissions.put("ORDER_ADMIN", linkedSetOf("ORDER_READ", "ORDER_WRITE", "ORDER_CANCEL"));
        permissions.put("FINANCE", linkedSetOf("PAYMENT_REFUND", "AUDIT_READ"));
        return permissions;
    }

    private Set<String> parseRoles(String rolesText) {
        if (rolesText == null || rolesText.isEmpty()) {
            return Collections.emptySet();
        }
        return linkedSetOf(rolesText.split(","));
    }

    private LinkedHashSet<String> linkedSetOf(String... values) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        Collections.addAll(result, values);
        return result;
    }

    public static final class LoginResult {

        private final List<String> steps;
        private final String sessionId;
        private final String token;
        private final boolean canWriteOrder;
        private final boolean validAfterBlacklist;

        public LoginResult(List<String> steps,
                           String sessionId,
                           String token,
                           boolean canWriteOrder,
                           boolean validAfterBlacklist) {
            this.steps = steps;
            this.sessionId = sessionId;
            this.token = token;
            this.canWriteOrder = canWriteOrder;
            this.validAfterBlacklist = validAfterBlacklist;
        }

        public List<String> steps() {
            return steps;
        }

        public String sessionId() {
            return sessionId;
        }

        public String token() {
            return token;
        }

        public boolean canWriteOrder() {
            return canWriteOrder;
        }

        public boolean validAfterBlacklist() {
            return validAfterBlacklist;
        }
    }

    public static final class TokenClaims {

        private final String subject;
        private final Set<String> roles;
        private final long expiresAtEpochSecond;

        public TokenClaims(String subject, Set<String> roles, long expiresAtEpochSecond) {
            this.subject = subject;
            this.roles = roles;
            this.expiresAtEpochSecond = expiresAtEpochSecond;
        }

        public String subject() {
            return subject;
        }

        public Set<String> roles() {
            return roles;
        }

        public long expiresAtEpochSecond() {
            return expiresAtEpochSecond;
        }
    }

    private static final class SessionRecord {

        private final String userId;

        private SessionRecord(String userId) {
            this.userId = userId;
        }

        public String userId() {
            return userId;
        }
    }
}
