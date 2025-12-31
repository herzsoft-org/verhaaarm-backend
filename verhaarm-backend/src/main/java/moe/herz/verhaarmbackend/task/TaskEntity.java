package moe.herz.verhaarmbackend.task;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "tasks")
public class TaskEntity {

	@Id
	@Column(nullable = false)
	private UUID id;

	@Column(name = "creator_user_id", nullable = false)
	private UUID creatorUserId;

	@Column(nullable = false)
	private String title;

	@Column(nullable = false)
	private String description;

	@Column(nullable = false)
	private boolean solved;

	@Column(name = "solved_at")
	private OffsetDateTime solvedAt;

	@Column(name = "deleted_at")
	private OffsetDateTime deletedAt;

	@Column(name = "due_at", nullable = false)
	private OffsetDateTime dueAt;

	@Column(name = "recurring_enabled", nullable = false)
	private boolean recurringEnabled;

	// e.g. "MON,WED,FRI" (empty for non-recurring)
	@Column(name = "recurring_days", nullable = false)
	private String recurringDays;

	@Column(name = "recurring_due_time")
	private LocalTime recurringDueTime;

	// The occurrence date this task currently belongs to (for recurring tasks)
	@Column(name = "recurring_open_for")
	private LocalDate recurringOpenFor;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private OffsetDateTime createdAt;

	@Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
	private OffsetDateTime updatedAt;

	@OneToMany(mappedBy = "task", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
	private Set<TaskAssigneeEntity> assignees = new HashSet<>();

	protected TaskEntity() {
		// JPA
	}

	public TaskEntity(UUID id, UUID creatorUserId, String title, String description, OffsetDateTime dueAt) {
		this.id = id;
		this.creatorUserId = creatorUserId;
		this.title = title;
		this.description = description;
		this.solved = false;
		this.solvedAt = null;
		this.deletedAt = null;
		this.dueAt = dueAt;
		this.recurringEnabled = false;
		this.recurringDays = "";
		this.recurringDueTime = null;
		this.recurringOpenFor = null;
	}

	public UUID getId() { return id; }
	public UUID getCreatorUserId() { return creatorUserId; }
	public String getTitle() { return title; }
	public String getDescription() { return description; }
	public boolean isSolved() { return solved; }
	public OffsetDateTime getSolvedAt() { return solvedAt; }
	public OffsetDateTime getDeletedAt() { return deletedAt; }
	public OffsetDateTime getDueAt() { return dueAt; }

	public boolean isRecurringEnabled() { return recurringEnabled; }
	public String getRecurringDays() { return recurringDays; }
	public LocalTime getRecurringDueTime() { return recurringDueTime; }
	public LocalDate getRecurringOpenFor() { return recurringOpenFor; }

	public OffsetDateTime getCreatedAt() { return createdAt; }
	public OffsetDateTime getUpdatedAt() { return updatedAt; }

	public Set<TaskAssigneeEntity> getAssignees() { return assignees; }

	public void setTitle(String title) { this.title = title; }
	public void setDescription(String description) { this.description = description; }

	public void setSolved(boolean solved) {
		this.solved = solved;
		this.solvedAt = solved ? OffsetDateTime.now() : null;
	}

	public void setDeletedAt(OffsetDateTime deletedAt) { this.deletedAt = deletedAt; }
	public boolean isDeleted() { return deletedAt != null; }

	public void setDueAt(OffsetDateTime dueAt) { this.dueAt = dueAt; }

	public void setRecurringEnabled(boolean recurringEnabled) { this.recurringEnabled = recurringEnabled; }
	public void setRecurringDays(String recurringDays) { this.recurringDays = recurringDays == null ? "" : recurringDays; }
	public void setRecurringDueTime(LocalTime recurringDueTime) { this.recurringDueTime = recurringDueTime; }
	public void setRecurringOpenFor(LocalDate recurringOpenFor) { this.recurringOpenFor = recurringOpenFor; }
}
