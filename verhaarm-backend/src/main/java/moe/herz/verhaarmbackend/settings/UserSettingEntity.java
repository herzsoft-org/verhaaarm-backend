package moe.herz.verhaarmbackend.settings;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
		name = "user_settings",
		uniqueConstraints = {
				@UniqueConstraint(name = "uq_user_settings_user_key", columnNames = {"user_id", "setting_key"})
		}
)
public class UserSettingEntity {

	@Id
	@Column(nullable = false, updatable = false)
	private UUID id;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Column(name = "setting_key", nullable = false)
	private String key;

	@Column(name = "setting_value", nullable = false)
	private String value;

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	protected UserSettingEntity() {
		// JPA
	}

	public UserSettingEntity(UUID id, UUID userId, String key, String value, OffsetDateTime updatedAt) {
		this.id = id;
		this.userId = userId;
		this.key = key;
		this.value = value;
		this.updatedAt = updatedAt;
	}

	@PrePersist
	void prePersist() {
		if (id == null) id = UUID.randomUUID();
		if (updatedAt == null) updatedAt = OffsetDateTime.now();
	}

	public UUID getId() { return id; }

	public UUID getUserId() { return userId; }

	public String getKey() { return key; }

	public String getValue() { return value; }
	public void setValue(String value) { this.value = value; }

	public OffsetDateTime getUpdatedAt() { return updatedAt; }
	public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}