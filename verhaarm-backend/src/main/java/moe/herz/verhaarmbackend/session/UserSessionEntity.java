package moe.herz.verhaarmbackend.session;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_sessions")
public class UserSessionEntity {

	@Id
	@Column(nullable = false, updatable = false)
	private UUID id;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Enumerated(EnumType.STRING)
	@Column(name = "app_type", nullable = false)
	private AppType appType = AppType.UNKNOWN;

	@Column(name = "device_name")
	private String deviceName;

	@Column(name = "device_model")
	private String deviceModel;

	@Column(name = "os_name")
	private String osName;

	@Column(name = "os_version")
	private String osVersion;

	@Column(name = "browser_name")
	private String browserName;

	@Column(name = "browser_version")
	private String browserVersion;

	@Column(name = "user_agent")
	private String userAgent;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	@Column(name = "last_active_at", nullable = false)
	private OffsetDateTime lastActiveAt;

	@Column(name = "expires_at")
	private OffsetDateTime expiresAt;

	@Column(name = "revoked_at")
	private OffsetDateTime revokedAt;

	protected UserSessionEntity() {}

	public UserSessionEntity(UUID id, UUID userId, AppType appType) {
		this.id = id;
		this.userId = userId;
		this.appType = appType == null ? AppType.UNKNOWN : appType;
	}

	@PrePersist
	void prePersist() {
		OffsetDateTime now = OffsetDateTime.now();
		if (id == null) id = UUID.randomUUID();
		if (createdAt == null) createdAt = now;
		if (lastActiveAt == null) lastActiveAt = now;
		if (appType == null) appType = AppType.UNKNOWN;
	}

	public boolean isRevoked() {
		return revokedAt != null;
	}

	public UUID getId() { return id; }
	public UUID getUserId() { return userId; }

	public AppType getAppType() { return appType; }
	public void setAppType(AppType appType) { this.appType = appType == null ? AppType.UNKNOWN : appType; }

	public String getDeviceName() { return deviceName; }
	public void setDeviceName(String deviceName) { this.deviceName = deviceName; }

	public String getDeviceModel() { return deviceModel; }
	public void setDeviceModel(String deviceModel) { this.deviceModel = deviceModel; }

	public String getOsName() { return osName; }
	public void setOsName(String osName) { this.osName = osName; }

	public String getOsVersion() { return osVersion; }
	public void setOsVersion(String osVersion) { this.osVersion = osVersion; }

	public String getBrowserName() { return browserName; }
	public void setBrowserName(String browserName) { this.browserName = browserName; }

	public String getBrowserVersion() { return browserVersion; }
	public void setBrowserVersion(String browserVersion) { this.browserVersion = browserVersion; }

	public String getUserAgent() { return userAgent; }
	public void setUserAgent(String userAgent) { this.userAgent = userAgent; }

	public OffsetDateTime getCreatedAt() { return createdAt; }

	public OffsetDateTime getLastActiveAt() { return lastActiveAt; }
	public void setLastActiveAt(OffsetDateTime lastActiveAt) { this.lastActiveAt = lastActiveAt; }

	public OffsetDateTime getExpiresAt() { return expiresAt; }
	public void setExpiresAt(OffsetDateTime expiresAt) { this.expiresAt = expiresAt; }

	public OffsetDateTime getRevokedAt() { return revokedAt; }
	public void setRevokedAt(OffsetDateTime revokedAt) { this.revokedAt = revokedAt; }
}