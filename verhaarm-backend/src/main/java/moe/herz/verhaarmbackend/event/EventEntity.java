package moe.herz.verhaarmbackend.event;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "events")
public class EventEntity {

	@Id
	@Column(nullable = false)
	private UUID id;

	@Column(name = "creator_user_id", nullable = false)
	private UUID creatorUserId;

	@Column(nullable = false)
	private String title;

	@Column(name = "starts_at", nullable = false)
	private OffsetDateTime startsAt;

	@Column(nullable = false)
	private boolean mandatory;

	@Enumerated(EnumType.STRING)
	@Column(name = "event_kind", nullable = false)
	private EventKind eventKind;

	@Enumerated(EnumType.STRING)
	@Column(name = "owner_type", nullable = false)
	private EventOwnerType ownerType;

	@Column(name = "deleted_at")
	private OffsetDateTime deletedAt;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private OffsetDateTime createdAt;

	@Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
	private OffsetDateTime updatedAt;

	protected EventEntity() {
		// JPA
	}

	public EventEntity(
			UUID id,
			UUID creatorUserId,
			String title,
			OffsetDateTime startsAt,
			boolean mandatory,
			EventKind eventKind,
			EventOwnerType ownerType
	) {
		this.id = id;
		this.creatorUserId = creatorUserId;
		this.title = title;
		this.startsAt = startsAt;
		this.mandatory = mandatory;
		this.eventKind = eventKind;
		this.ownerType = ownerType;
	}

	public UUID getId() { return id; }
	public UUID getCreatorUserId() { return creatorUserId; }
	public String getTitle() { return title; }
	public OffsetDateTime getStartsAt() { return startsAt; }
	public boolean isMandatory() { return mandatory; }
	public EventKind getEventKind() { return eventKind; }
	public EventOwnerType getOwnerType() { return ownerType; }
	public OffsetDateTime getDeletedAt() { return deletedAt; }
	public OffsetDateTime getCreatedAt() { return createdAt; }
	public OffsetDateTime getUpdatedAt() { return updatedAt; }

	public void setTitle(String title) { this.title = title; }
	public void setStartsAt(OffsetDateTime startsAt) { this.startsAt = startsAt; }
	public void setMandatory(boolean mandatory) { this.mandatory = mandatory; }
	public void setEventKind(EventKind eventKind) { this.eventKind = eventKind; }
	public void setOwnerType(EventOwnerType ownerType) { this.ownerType = ownerType; }
	public void setDeletedAt(OffsetDateTime deletedAt) { this.deletedAt = deletedAt; }

	public boolean isDeleted() { return deletedAt != null; }
}