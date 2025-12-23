package moe.herz.verhaarmbackend.fine;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "fines")
public class FineEntity {

	@Id
	@Column(nullable = false)
	private UUID id;

	@Column(name = "period_id", nullable = false)
	private UUID periodId;

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

	@Column(name = "suggester_user_id")
	private UUID suggesterUserId;

	@Column(name = "deleted_at")
	private OffsetDateTime deletedAt;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private OffsetDateTime createdAt;

	@Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
	private OffsetDateTime updatedAt;

	@ElementCollection
	@CollectionTable(
			name = "fine_targets",
			joinColumns = @JoinColumn(name = "fine_id")
	)
	@Column(name = "user_id", nullable = false)
	private Set<UUID> targetUserIds = new HashSet<>();

	protected FineEntity() {
		// JPA
	}

	public FineEntity(UUID id, UUID periodId, UUID creatorUserId, UUID catalogItemId, String reason, int amountCents, FineType type) {
		this.id = id;
		this.periodId = periodId;
		this.creatorUserId = creatorUserId;
		this.catalogItemId = catalogItemId;
		this.reason = reason;
		this.amountCents = amountCents;
		this.type = type;
	}

	public UUID getId() { return id; }
	public UUID getPeriodId() { return periodId; }
	public UUID getCreatorUserId() { return creatorUserId; }
	public UUID getCatalogItemId() { return catalogItemId; }
	public String getReason() { return reason; }
	public int getAmountCents() { return amountCents; }
	public FineType getType() { return type; }
	public UUID getSuggesterUserId() { return suggesterUserId; }
	public OffsetDateTime getDeletedAt() { return deletedAt; }
	public OffsetDateTime getCreatedAt() { return createdAt; }
	public OffsetDateTime getUpdatedAt() { return updatedAt; }
	public Set<UUID> getTargetUserIds() { return targetUserIds; }

	public void setPeriodId(UUID periodId) { this.periodId = periodId; }
	public void setCatalogItemId(UUID catalogItemId) { this.catalogItemId = catalogItemId; }
	public void setReason(String reason) { this.reason = reason; }
	public void setAmountCents(int amountCents) { this.amountCents = amountCents; }
	public void setType(FineType type) { this.type = type; }
	public void setSuggesterUserId(UUID suggesterUserId) { this.suggesterUserId = suggesterUserId; }
	public void setDeletedAt(OffsetDateTime deletedAt) { this.deletedAt = deletedAt; }

	public boolean isDeleted() { return deletedAt != null; }

	public void clearTargets() { this.targetUserIds.clear(); }
	public void addTarget(UUID userId) { this.targetUserIds.add(userId); }
}
