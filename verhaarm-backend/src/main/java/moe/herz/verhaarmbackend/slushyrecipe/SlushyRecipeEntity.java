package moe.herz.verhaarmbackend.slushyrecipe;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "slushy_recipes")
public class SlushyRecipeEntity {

	@Id
	@Column(nullable = false)
	private UUID id;

	@Column(nullable = false)
	private String title;

	@Column
	private String description;

	@Column(name = "created_by_user_id")
	private UUID createdByUserId;

	@Column(name = "deleted_at")
	private OffsetDateTime deletedAt;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private OffsetDateTime createdAt;

	@Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
	private OffsetDateTime updatedAt;

	protected SlushyRecipeEntity() {
		// JPA
	}

	public SlushyRecipeEntity(UUID id, String title, String description, UUID createdByUserId) {
		this.id = id;
		this.title = title;
		this.description = description;
		this.createdByUserId = createdByUserId;
	}

	public UUID getId() { return id; }
	public String getTitle() { return title; }
	public String getDescription() { return description; }
	public UUID getCreatedByUserId() { return createdByUserId; }
	public OffsetDateTime getDeletedAt() { return deletedAt; }
	public OffsetDateTime getCreatedAt() { return createdAt; }
	public OffsetDateTime getUpdatedAt() { return updatedAt; }

	public boolean isDeleted() { return deletedAt != null; }

	public void setTitle(String title) { this.title = title; }
	public void setDescription(String description) { this.description = description; }
	public void setDeletedAt(OffsetDateTime deletedAt) { this.deletedAt = deletedAt; }
}
