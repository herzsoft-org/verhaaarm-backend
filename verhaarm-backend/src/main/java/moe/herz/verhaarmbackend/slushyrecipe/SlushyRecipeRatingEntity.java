package moe.herz.verhaarmbackend.slushyrecipe;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "slushy_recipe_ratings")
public class SlushyRecipeRatingEntity {

	@Id
	@Column(nullable = false)
	private UUID id;

	@Column(name = "recipe_id", nullable = false)
	private UUID recipeId;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Column(nullable = false)
	private int stars;

	@Column
	private String comment;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private OffsetDateTime createdAt;

	@Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
	private OffsetDateTime updatedAt;

	protected SlushyRecipeRatingEntity() {
		// JPA
	}

	public SlushyRecipeRatingEntity(UUID id, UUID recipeId, UUID userId, int stars, String comment) {
		this.id = id;
		this.recipeId = recipeId;
		this.userId = userId;
		this.stars = stars;
		this.comment = comment;
	}

	public UUID getId() { return id; }
	public UUID getRecipeId() { return recipeId; }
	public UUID getUserId() { return userId; }
	public int getStars() { return stars; }
	public String getComment() { return comment; }
	public OffsetDateTime getCreatedAt() { return createdAt; }
	public OffsetDateTime getUpdatedAt() { return updatedAt; }

	public void setStars(int stars) { this.stars = stars; }
	public void setComment(String comment) { this.comment = comment; }
}
