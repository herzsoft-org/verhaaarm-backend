package moe.herz.verhaarmbackend.paukstunde;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "paukstunden")
public class PaukstundeEntity {

	@Id
	@Column(nullable = false)
	private UUID id;

	@Column(name = "training_date", nullable = false)
	private LocalDate date;

	@Column(nullable = false)
	private int hours;

	@Column(name = "created_by_user_id", nullable = false)
	private UUID createdByUserId;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private OffsetDateTime createdAt;

	@Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
	private OffsetDateTime updatedAt;

	@ElementCollection
	@CollectionTable(
			name = "paukstunde_participants",
			joinColumns = @JoinColumn(name = "paukstunde_id")
	)
	@Column(name = "user_id", nullable = false)
	private Set<UUID> participantUserIds = new HashSet<>();

	protected PaukstundeEntity() {
		// JPA
	}

	public PaukstundeEntity(UUID id, LocalDate date, int hours, UUID createdByUserId) {
		this.id = id;
		this.date = date;
		this.hours = hours;
		this.createdByUserId = createdByUserId;
	}

	public UUID getId() { return id; }
	public LocalDate getDate() { return date; }
	public int getHours() { return hours; }
	public UUID getCreatedByUserId() { return createdByUserId; }
	public OffsetDateTime getCreatedAt() { return createdAt; }
	public OffsetDateTime getUpdatedAt() { return updatedAt; }
	public Set<UUID> getParticipantUserIds() { return participantUserIds; }

	public void setDate(LocalDate date) { this.date = date; }
	public void setHours(int hours) { this.hours = hours; }
	public void clearParticipants() { participantUserIds.clear(); }
	public void addParticipant(UUID userId) { participantUserIds.add(userId); }
}
