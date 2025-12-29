package moe.herz.verhaarmbackend.push;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "push_devices")
public class PushDeviceEntity {

	@Id
	@Column(nullable = false)
	private UUID id;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private PushDeviceKind kind;

	// WebPush
	@Column
	private String endpoint;

	@Column
	private String p256dh;

	@Column
	private String auth;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "subscription_json", columnDefinition = "jsonb")
	private Map<String, Object> subscriptionJson;

	// FCM
	@Column(name = "fcm_token")
	private String fcmToken;

	@Column(name = "user_agent")
	private String userAgent;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt = OffsetDateTime.now();

	@Column(name = "last_seen_at", nullable = false)
	private OffsetDateTime lastSeenAt = OffsetDateTime.now();

	protected PushDeviceEntity() {
	}

	public PushDeviceEntity(UUID id, UUID userId, PushDeviceKind kind) {
		this.id = id;
		this.userId = userId;
		this.kind = kind;
	}

	public UUID getId() {
		return id;
	}

	public UUID getUserId() {
		return userId;
	}

	public PushDeviceKind getKind() {
		return kind;
	}

	public String getEndpoint() {
		return endpoint;
	}

	public void setEndpoint(String endpoint) {
		this.endpoint = endpoint;
	}

	public String getP256dh() {
		return p256dh;
	}

	public void setP256dh(String p256dh) {
		this.p256dh = p256dh;
	}

	public String getAuth() {
		return auth;
	}

	public void setAuth(String auth) {
		this.auth = auth;
	}

	public Map<String, Object> getSubscriptionJson() {
		return subscriptionJson;
	}

	public void setSubscriptionJson(Map<String, Object> subscriptionJson) {
		this.subscriptionJson = subscriptionJson;
	}

	public String getFcmToken() {
		return fcmToken;
	}

	public void setFcmToken(String fcmToken) {
		this.fcmToken = fcmToken;
	}

	public String getUserAgent() {
		return userAgent;
	}

	public void setUserAgent(String userAgent) {
		this.userAgent = userAgent;
	}

	public OffsetDateTime getCreatedAt() {
		return createdAt;
	}

	public OffsetDateTime getLastSeenAt() {
		return lastSeenAt;
	}

	public void setLastSeenAt(OffsetDateTime lastSeenAt) {
		this.lastSeenAt = lastSeenAt;
	}
}
