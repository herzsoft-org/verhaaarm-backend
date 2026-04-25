package moe.herz.verhaarmbackend.auth;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
public class RefreshTokenEntity {

	@Id
	@Column(nullable = false, updatable = false)
	private UUID id;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Column(name = "session_id")
	private UUID sessionId;

	@Column(name = "token_hash", nullable = false, unique = true)
	private String tokenHash;

	@Column(name = "expires_at", nullable = false)
	private OffsetDateTime expiresAt;

	@Column(name = "revoked", nullable = false)
	private boolean revoked;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	protected RefreshTokenEntity() {}

	public RefreshTokenEntity(UUID id, UUID userId, UUID sessionId, String tokenHash, OffsetDateTime expiresAt) {
		this.id = id;
		this.userId = userId;
		this.sessionId = sessionId;
		this.tokenHash = tokenHash;
		this.expiresAt = expiresAt;
		this.revoked = false;
		this.createdAt = OffsetDateTime.now();
	}

	public UUID getId() { return id; }
	public UUID getUserId() { return userId; }

	public UUID getSessionId() { return sessionId; }
	public void setSessionId(UUID sessionId) { this.sessionId = sessionId; }

	public String getTokenHash() { return tokenHash; }

	public OffsetDateTime getExpiresAt() { return expiresAt; }

	public boolean isRevoked() { return revoked; }
	public void setRevoked(boolean revoked) { this.revoked = revoked; }

	public OffsetDateTime getCreatedAt() { return createdAt; }

	public boolean isExpired() {
		return OffsetDateTime.now().isAfter(expiresAt);
	}
}