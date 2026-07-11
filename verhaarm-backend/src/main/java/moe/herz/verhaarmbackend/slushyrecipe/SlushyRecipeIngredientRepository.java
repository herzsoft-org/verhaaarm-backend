package moe.herz.verhaarmbackend.slushyrecipe;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SlushyRecipeIngredientRepository extends JpaRepository<SlushyRecipeIngredientEntity, UUID> {

	List<SlushyRecipeIngredientEntity> findByRecipeIdOrderBySortOrderAsc(UUID recipeId);

	void deleteByRecipeId(UUID recipeId);
}
