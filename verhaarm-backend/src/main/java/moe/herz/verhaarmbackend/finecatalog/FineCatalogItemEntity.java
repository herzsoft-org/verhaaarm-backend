package moe.herz.verhaarmbackend.finecatalog;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "fine_catalog_items")
public class FineCatalogItemEntity {

	@Id
	@Column(nullable = false)
	private UUID id;

	@Column(nullable = false)
	private String title;

	@Column(name = "default_amount_cents", nullable = false)
	private int defaultAmountCents;

	@Column(nullable = false)
	private boolean active;

	@Column(name = "deleted_at")
	private OffsetDateTime deletedAt;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private OffsetDateTime createdAt;

	@Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
	private OffsetDateTime updatedAt;

	protected FineCatalogItemEntity() {
		// JPA
	}

	public FineCatalogItemEntity(UUID id, String title, int defaultAmountCents, boolean active, OffsetDateTime deletedAt) {
		this.id = id;
		this.title = title;
		this.defaultAmountCents = defaultAmountCents;
		this.active = active;
		this.deletedAt = deletedAt;
	}

	public UUID getId() { return id; }
	public String getTitle() { return title; }
	public int getDefaultAmountCents() { return defaultAmountCents; }
	public boolean isActive() { return active; }
	public OffsetDateTime getDeletedAt() { return deletedAt; }
	public OffsetDateTime getCreatedAt() { return createdAt; }
	public OffsetDateTime getUpdatedAt() { return updatedAt; }

	public void setTitle(String title) { this.title = title; }
	public void setDefaultAmountCents(int defaultAmountCents) { this.defaultAmountCents = defaultAmountCents; }
	public void setActive(boolean active) { this.active = active; }
	public void setDeletedAt(OffsetDateTime deletedAt) { this.deletedAt = deletedAt; }

	public boolean isDeleted() { return deletedAt != null; }
}
