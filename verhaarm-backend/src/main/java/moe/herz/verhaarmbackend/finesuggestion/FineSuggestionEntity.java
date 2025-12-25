package moe.herz.verhaarmbackend.finesuggestion;

import jakarta.persistence.*;
import moe.herz.verhaarmbackend.fine.FineType;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "fine_suggestions")
public class FineSuggestionEntity {

	@Id
	@Column(nullable = false)
	private UUID id;

	@Column(name = "fine_date", nullable = false)
	private LocalDate fineDate;

	@Column(name = "creator_user_id", nullable = false)
	private UUID creatorUserId;

	@Column(name = "catalog_item_id")
	private UUID catalogItemId;

	@Column(nullable = false)
	private String reason;

	@Column(name = "amount_cents", nullable = false)
	private int amountCents;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private FineType type;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private FineSuggestionStatus status;

	@Column(name = "decided_by_user_id")
	private UUID decidedByUserId;

	@Column(name = "decided_at")
	private OffsetDateTime decidedAt;

	@Column(name = "accepted_fine_id")
	private UUID acceptedFineId;

	@Column(name = "deleted_at")
	private OffsetDateTime deletedAt;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private OffsetDateTime createdAt;

	@Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
	private OffsetDateTime updatedAt;

	@ElementCollection
	@CollectionTable(
			name = "fine_suggestion_targets",
			joinColumns = @JoinColumn(name = "suggestion_id")
	)
	@Column(name = "user_id", nullable = false)
	private Set<UUID> targetUserIds = new HashSet<>();

	protected FineSuggestionEntity() {
		// JPA
	}

	public FineSuggestionEntity(
			UUID id,
			LocalDate fineDate,
			UUID creatorUserId,
			UUID catalogItemId,
			String reason,
			int amountCents,
			FineType type
	) {
		this.id = id;
		this.fineDate = fineDate;
		this.creatorUserId = creatorUserId;
		this.catalogItemId = catalogItemId;
		this.reason = reason;
		this.amountCents = amountCents;
		this.type = type;
		this.status = FineSuggestionStatus.PENDING;
	}

	public UUID getId() { return id; }
	public LocalDate getFineDate() { return fineDate; }
	public UUID getCreatorUserId() { return creatorUserId; }
	public UUID getCatalogItemId() { return catalogItemId; }
	public String getReason() { return reason; }
	public int getAmountCents() { return amountCents; }
	public FineType getType() { return type; }
	public FineSuggestionStatus getStatus() { return status; }
	public UUID getDecidedByUserId() { return decidedByUserId; }
	public OffsetDateTime getDecidedAt() { return decidedAt; }
	public UUID getAcceptedFineId() { return acceptedFineId; }
	public OffsetDateTime getDeletedAt() { return deletedAt; }
	public OffsetDateTime getCreatedAt() { return createdAt; }
	public OffsetDateTime getUpdatedAt() { return updatedAt; }
	public Set<UUID> getTargetUserIds() { return targetUserIds; }

	public boolean isDeleted() { return deletedAt != null; }

	public void setFineDate(LocalDate fineDate) { this.fineDate = fineDate; }
	public void setDeletedAt(OffsetDateTime deletedAt) { this.deletedAt = deletedAt; }

	public void clearTargets() { this.targetUserIds.clear(); }
	public void addTarget(UUID userId) { this.targetUserIds.add(userId); }

	public void markAccepted(UUID decidedByUserId, UUID fineId) {
		this.status = FineSuggestionStatus.ACCEPTED;
		this.decidedByUserId = decidedByUserId;
		this.decidedAt = OffsetDateTime.now();
		this.acceptedFineId = fineId;
	}

	public void markRejected(UUID decidedByUserId) {
		this.status = FineSuggestionStatus.REJECTED;
		this.decidedByUserId = decidedByUserId;
		this.decidedAt = OffsetDateTime.now();
	}
}
