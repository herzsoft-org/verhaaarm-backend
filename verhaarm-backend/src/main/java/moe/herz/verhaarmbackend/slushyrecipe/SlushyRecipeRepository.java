package moe.herz.verhaarmbackend.slushyrecipe;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SlushyRecipeRepository extends JpaRepository<SlushyRecipeEntity, UUID> {

	@Query("select r from SlushyRecipeEntity r where r.deletedAt is null order by r.createdAt desc")
	List<SlushyRecipeEntity> findAllVisible();

	@Query("select r from SlushyRecipeEntity r where r.id = :id and r.deletedAt is null")
	Optional<SlushyRecipeEntity> findVisibleById(UUID id);
}
