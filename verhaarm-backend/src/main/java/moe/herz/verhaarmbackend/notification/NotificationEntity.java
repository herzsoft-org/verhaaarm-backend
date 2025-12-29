package moe.herz.verhaarmbackend.notification;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "notifications")
public class NotificationEntity {

	@Id
	@Column(nullable = false)
	private UUID id;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private NotificationType type;

	@Column(nullable = false)
	private String title;

	@Column(nullable = false)
	private String body;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, columnDefinition = "jsonb")
	private Map<String, Object> data = new HashMap<>();

	@Column(name = "read_at")
	private OffsetDateTime readAt;

	@Column(name = "deleted_at")
	private OffsetDateTime deletedAt;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt = OffsetDateTime.now();

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt = OffsetDateTime.now();

	protected NotificationEntity() {
	}

	public NotificationEntity(UUID id, UUID userId, NotificationType type, String title, String body, Map<String, Object> data) {
		this.id = id;
		this.userId = userId;
		this.type = type;
		this.title = title;
		this.body = body;
		this.data = (data == null) ? new HashMap<>() : new HashMap<>(data);
	}

	public UUID getId() {
		return id;
	}

	public UUID getUserId() {
		return userId;
	}

	public NotificationType getType() {
		return type;
	}

	public String getTitle() {
		return title;
	}

	public String getBody() {
		return body;
	}

	public Map<String, Object> getData() {
		return data;
	}

	public OffsetDateTime getReadAt() {
		return readAt;
	}

	public void setReadAt(OffsetDateTime readAt) {
		this.readAt = readAt;
	}

	public OffsetDateTime getDeletedAt() {
		return deletedAt;
	}

	public void setDeletedAt(OffsetDateTime deletedAt) {
		this.deletedAt = deletedAt;
	}

	public OffsetDateTime getCreatedAt() {
		return createdAt;
	}

	public OffsetDateTime getUpdatedAt() {
		return updatedAt;
	}
}
