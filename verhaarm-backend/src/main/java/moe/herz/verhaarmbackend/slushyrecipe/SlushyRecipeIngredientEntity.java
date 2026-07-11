package moe.herz.verhaarmbackend.slushyrecipe;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "slushy_recipe_ingredients")
public class SlushyRecipeIngredientEntity {

	@Id
	@Column(nullable = false)
	private UUID id;

	@Column(name = "recipe_id", nullable = false)
	private UUID recipeId;

	@Column(nullable = false)
	private String name;

	@Column
	private String amount;

	@Column(name = "sort_order", nullable = false)
	private int sortOrder;

	protected SlushyRecipeIngredientEntity() {
		// JPA
	}

	public SlushyRecipeIngredientEntity(UUID id, UUID recipeId, String name, String amount, int sortOrder) {
		this.id = id;
		this.recipeId = recipeId;
		this.name = name;
		this.amount = amount;
		this.sortOrder = sortOrder;
	}

	public UUID getId() { return id; }
	public UUID getRecipeId() { return recipeId; }
	public String getName() { return name; }
	public String getAmount() { return amount; }
	public int getSortOrder() { return sortOrder; }
}
