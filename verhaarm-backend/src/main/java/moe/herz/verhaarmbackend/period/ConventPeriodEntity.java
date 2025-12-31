package moe.herz.verhaarmbackend.period;

import jakarta.persistence.*;

import java.time.LocalDate;
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

	@Column(name = "start_date", nullable = false)
	private LocalDate startAt;

	@Column(name = "end_date", nullable = false)
	private LocalDate endAt;

	@Column(nullable = false)
	private boolean locked;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private OffsetDateTime createdAt;

	@Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
	private OffsetDateTime updatedAt;

	protected ConventPeriodEntity() {
		// JPA
	}

	public ConventPeriodEntity(UUID id, String semester, LocalDate startAt, LocalDate endAt, boolean locked) {
		this.id = id;
		this.semester = semester;
		this.startAt = startAt;
		this.endAt = endAt;
		this.locked = locked;
	}

	public UUID getId() { return id; }
	public String getSemester() { return semester; }
	public LocalDate getStartAt() { return startAt; }
	public LocalDate getEndAt() { return endAt; }
	public boolean isLocked() { return locked; }
	public OffsetDateTime getCreatedAt() { return createdAt; }
	public OffsetDateTime getUpdatedAt() { return updatedAt; }

	public void setSemester(String semester) { this.semester = semester; }
	public void setStartAt(LocalDate startAt) { this.startAt = startAt; }
	public void setEndAt(LocalDate endAt) { this.endAt = endAt; }
	public void setLocked(boolean locked) { this.locked = locked; }
}
