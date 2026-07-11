package moe.herz.verhaarmbackend.slushyrecipe;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SlushyRecipeRatingRepository extends JpaRepository<SlushyRecipeRatingEntity, UUID> {

	@Query("select r from SlushyRecipeRatingEntity r where r.recipeId = :recipeId order by r.createdAt asc")
	List<SlushyRecipeRatingEntity> findByRecipeId(UUID recipeId);

	Optional<SlushyRecipeRatingEntity> findByRecipeIdAndUserId(UUID recipeId, UUID userId);

	long countByRecipeId(UUID recipeId);

	@Query("select avg(r.stars) from SlushyRecipeRatingEntity r where r.recipeId = :recipeId")
	Double averageStarsByRecipeId(UUID recipeId);
}
