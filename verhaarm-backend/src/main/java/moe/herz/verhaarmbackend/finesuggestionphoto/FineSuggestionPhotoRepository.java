package moe.herz.verhaarmbackend.finesuggestionphoto;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FineSuggestionPhotoRepository extends JpaRepository<FineSuggestionPhotoEntity, UUID> {

	@Query("""
        select p from FineSuggestionPhotoEntity p
        where p.suggestionId = :suggestionId and p.deletedAt is null
        order by p.createdAt asc
    """)
	List<FineSuggestionPhotoEntity> findVisibleBySuggestionId(UUID suggestionId);

	@Query("""
        select p from FineSuggestionPhotoEntity p
        where p.id = :photoId and p.deletedAt is null
    """)
	Optional<FineSuggestionPhotoEntity> findVisibleById(UUID photoId);

	@Query("""
        select p from FineSuggestionPhotoEntity p
        where p.id = :photoId and p.suggestionId = :suggestionId and p.deletedAt is null
    """)
	Optional<FineSuggestionPhotoEntity> findVisibleByIdAndSuggestionId(UUID photoId, UUID suggestionId);
}