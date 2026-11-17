package model;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain model for a user session.
 */
public class Session {

    private final String sessionId;
    private final int userId;
    private final User user;
    private final Instant createdAt;
    private final Instant expiresAt;

    public Session(String sessionId, int userId, User user, Instant createdAt, Instant expiresAt) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.user = user != null ? user : new User(userId, "", "", "");
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.expiresAt = expiresAt;
    }

    public Session(String sessionId, User user) {
        this(sessionId, user.userId(), user, Instant.now(), null);
    }

    public String getSessionId() { return sessionId; }
    public int getUserId() { return userId; }
    public User getUser() { return user; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }

    public boolean isValid() {
        if (expiresAt == null) return true;
        return Instant.now().isBefore(expiresAt);
    }

    public static String generateSessionId() {
        return UUID.randomUUID().toString();
    }
}
