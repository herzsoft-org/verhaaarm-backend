package moe.herz.verhaarmbackend.liveevent;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
		name = "live_event_reactions",
		uniqueConstraints = @UniqueConstraint(
				name = "uq_live_event_reactions_event_user_type",
				columnNames = {"live_event_id", "user_id", "type"}
		)
)
public class LiveEventReactionEntity {

	@Id
	@Column(nullable = false)
	private UUID id;

	@Column(name = "live_event_id", nullable = false)
	private UUID liveEventId;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private LiveEventReactionType type;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private OffsetDateTime createdAt;

	protected LiveEventReactionEntity() {
		// JPA
	}

	public LiveEventReactionEntity(UUID id, UUID liveEventId, UUID userId, LiveEventReactionType type) {
		this.id = id;
		this.liveEventId = liveEventId;
		this.userId = userId;
		this.type = type;
	}

	public UUID getId() { return id; }
	public UUID getLiveEventId() { return liveEventId; }
	public UUID getUserId() { return userId; }
	public LiveEventReactionType getType() { return type; }
	public OffsetDateTime getCreatedAt() { return createdAt; }
}
