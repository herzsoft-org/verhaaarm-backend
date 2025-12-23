package moe.herz.verhaarmbackend.finesuggestion;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FineSuggestionRepository extends JpaRepository<FineSuggestionEntity, UUID> {

	@Query("""
		select s from FineSuggestionEntity s
		where s.deletedAt is null
		and s.id = :id
	""")
	Optional<FineSuggestionEntity> findVisibleById(@Param("id") UUID id);

	@Query("""
		select s from FineSuggestionEntity s
		where s.deletedAt is null
		and s.status = :status
		order by s.createdAt desc
	""")
	List<FineSuggestionEntity> findVisibleByStatus(@Param("status") FineSuggestionStatus status);

	@Query("""
		select s from FineSuggestionEntity s
		where s.deletedAt is null
		order by s.createdAt desc
	""")
	List<FineSuggestionEntity> findAllVisible();
}
