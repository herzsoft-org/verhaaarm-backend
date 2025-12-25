package moe.herz.verhaarmbackend.period;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "convent_periods")
public class ConventPeriodEntity {

	@Id
	@Column(nullable = false)
	private UUID id;

	// Multiple periods may share the same semester label (e.g., WS24/25)
	@Column(nullable = false)
	private String semester;

	@Column(name = "start_at", nullable = false)
	private OffsetDateTime startAt;

	@Column(name = "end_at", nullable = false)
	private OffsetDateTime endAt;

	@Column(nullable = false)
	private boolean active;

	@Column(nullable = false)
	private boolean locked;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private OffsetDateTime createdAt;

	@Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
	private OffsetDateTime updatedAt;

	protected ConventPeriodEntity() {
		// JPA
	}

	public ConventPeriodEntity(UUID id, String semester, OffsetDateTime startAt, OffsetDateTime endAt, boolean active, boolean locked) {
		this.id = id;
		this.semester = semester;
		this.startAt = startAt;
		this.endAt = endAt;
		this.active = active;
		this.locked = locked;
	}

	public UUID getId() { return id; }
	public String getSemester() { return semester; }
	public OffsetDateTime getStartAt() { return startAt; }
	public OffsetDateTime getEndAt() { return endAt; }
	public boolean isActive() { return active; }
	public boolean isLocked() { return locked; }
	public OffsetDateTime getCreatedAt() { return createdAt; }
	public OffsetDateTime getUpdatedAt() { return updatedAt; }

	public void setSemester(String semester) { this.semester = semester; }
	public void setStartAt(OffsetDateTime startAt) { this.startAt = startAt; }
	public void setEndAt(OffsetDateTime endAt) { this.endAt = endAt; }
	public void setActive(boolean active) { this.active = active; }
	public void setLocked(boolean locked) { this.locked = locked; }
}
