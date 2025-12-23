package moe.herz.verhaarmbackend.finesuggestion;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FineSuggestionRepository extends JpaRepository<FineSuggestionEntity, UUID> {

	@Query("select s from FineSuggestionEntity s where s.deletedAt is null order by s.createdAt desc")
	List<FineSuggestionEntity> findAllVisible();

	@Query("select s from FineSuggestionEntity s where s.deletedAt is null and s.status = :status order by s.createdAt desc")
	List<FineSuggestionEntity> findVisibleByStatus(FineSuggestionStatus status);

	@Query("select s from FineSuggestionEntity s where s.id = :id and s.deletedAt is null")
	Optional<FineSuggestionEntity> findVisibleById(UUID id);

	@Query("""
		select s from FineSuggestionEntity s
		where s.deletedAt is null
		  and s.creatorUserId = :creatorUserId
		order by s.createdAt desc
	""")
	List<FineSuggestionEntity> findVisibleByCreator(UUID creatorUserId);

	@Query("""
		select s from FineSuggestionEntity s
		where s.deletedAt is null
		  and s.creatorUserId = :creatorUserId
		  and s.status = :status
		order by s.createdAt desc
	""")
	List<FineSuggestionEntity> findVisibleByCreatorAndStatus(UUID creatorUserId, FineSuggestionStatus status);
}
