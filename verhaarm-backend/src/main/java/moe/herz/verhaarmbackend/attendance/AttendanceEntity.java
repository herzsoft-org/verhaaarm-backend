package moe.herz.verhaarmbackend.attendance;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "attendance")
public class AttendanceEntity {

	@Id
	@Column(nullable = false)
	private UUID id;

	@Column(name = "event_id", nullable = false)
	private UUID eventId;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private AttendanceStatus status;

	@Column(name = "late_minutes")
	private Integer lateMinutes;

	@Column(name = "fine_id")
	private UUID fineId;

	@Column(name = "deleted_at")
	private OffsetDateTime deletedAt;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private OffsetDateTime createdAt;

	@Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
	private OffsetDateTime updatedAt;

	protected AttendanceEntity() {
		// JPA
	}

	public AttendanceEntity(UUID id, UUID eventId, UUID userId, AttendanceStatus status, Integer lateMinutes) {
		this.id = id;
		this.eventId = eventId;
		this.userId = userId;
		this.status = status;
		this.lateMinutes = lateMinutes;
	}

	public UUID getId() { return id; }
	public UUID getEventId() { return eventId; }
	public UUID getUserId() { return userId; }
	public AttendanceStatus getStatus() { return status; }
	public Integer getLateMinutes() { return lateMinutes; }
	public UUID getFineId() { return fineId; }
	public OffsetDateTime getDeletedAt() { return deletedAt; }
	public OffsetDateTime getCreatedAt() { return createdAt; }
	public OffsetDateTime getUpdatedAt() { return updatedAt; }

	public void setStatus(AttendanceStatus status) { this.status = status; }
	public void setLateMinutes(Integer lateMinutes) { this.lateMinutes = lateMinutes; }
	public void setFineId(UUID fineId) { this.fineId = fineId; }
	public void setDeletedAt(OffsetDateTime deletedAt) { this.deletedAt = deletedAt; }

	public boolean isDeleted() { return deletedAt != null; }
}
