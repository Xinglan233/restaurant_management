package com.example.restaurant;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthService {
    private static final long SESSION_TTL_SECONDS = 8 * 60 * 60;

    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, SessionUser> sessions = new ConcurrentHashMap<>();

    public String createToken(Map<String, Object> user) {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        sessions.put(token, new SessionUser(
                String.valueOf(user.get("id")),
                String.valueOf(user.get("username")),
                String.valueOf(user.get("role")),
                Instant.now().plusSeconds(SESSION_TTL_SECONDS)));
        return token;
    }

    public Optional<SessionUser> authenticate(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return Optional.empty();
        }
        String token = authorizationHeader.substring("Bearer ".length()).trim();
        SessionUser user = sessions.get(token);
        if (user == null) {
            return Optional.empty();
        }
        if (user.expiresAt().isBefore(Instant.now())) {
            sessions.remove(token);
            return Optional.empty();
        }
        return Optional.of(user);
    }

    public boolean isManager(SessionUser user) {
        return "店长".equals(user.role()) || "管理员".equals(user.role());
    }

    public record SessionUser(String id, String username, String role, Instant expiresAt) {
    }
}
