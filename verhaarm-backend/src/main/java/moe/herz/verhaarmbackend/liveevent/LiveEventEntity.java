package moe.herz.verhaarmbackend.liveevent;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "live_events")
public class LiveEventEntity {

	@Id
	@Column(nullable = false)
	private UUID id;

	@Column(nullable = false)
	private String title;

	@Column(nullable = false)
	private String place;

	@Column(nullable = false)
	private String description;

	@Column(name = "created_by_user_id", nullable = false)
	private UUID createdByUserId;

	@Column(name = "expires_at", nullable = false)
	private OffsetDateTime expiresAt;

	@Column(name = "deleted_at")
	private OffsetDateTime deletedAt;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private OffsetDateTime createdAt;

	@Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
	private OffsetDateTime updatedAt;

	protected LiveEventEntity() {
		// JPA
	}

	public LiveEventEntity(UUID id, String title, String place, String description, UUID createdByUserId, OffsetDateTime expiresAt) {
		this.id = id;
		this.title = title;
		this.place = place;
		this.description = description;
		this.createdByUserId = createdByUserId;
		this.expiresAt = expiresAt;
	}

	public UUID getId() { return id; }
	public String getTitle() { return title; }
	public String getPlace() { return place; }
	public String getDescription() { return description; }
	public UUID getCreatedByUserId() { return createdByUserId; }
	public OffsetDateTime getExpiresAt() { return expiresAt; }
	public OffsetDateTime getDeletedAt() { return deletedAt; }
	public OffsetDateTime getCreatedAt() { return createdAt; }
	public OffsetDateTime getUpdatedAt() { return updatedAt; }

	public void setTitle(String title) { this.title = title; }
	public void setPlace(String place) { this.place = place; }
	public void setDescription(String description) { this.description = description; }
	public void setExpiresAt(OffsetDateTime expiresAt) { this.expiresAt = expiresAt; }
	public void setDeletedAt(OffsetDateTime deletedAt) { this.deletedAt = deletedAt; }

	public boolean isDeleted() { return deletedAt != null; }
}
