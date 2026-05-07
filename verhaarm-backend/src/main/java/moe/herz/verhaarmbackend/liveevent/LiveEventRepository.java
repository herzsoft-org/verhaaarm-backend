package moe.herz.verhaarmbackend.liveevent;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LiveEventRepository extends JpaRepository<LiveEventEntity, UUID> {

	@Query("""
		select e from LiveEventEntity e
		where e.deletedAt is null
		  and e.expiresAt > :now
		order by e.createdAt desc
	""")
	List<LiveEventEntity> findActiveVisible(OffsetDateTime now);

	@Query("""
		select e from LiveEventEntity e
		where e.id = :id
		  and e.deletedAt is null
	""")
	Optional<LiveEventEntity> findVisibleById(UUID id);

	boolean existsBySourceEventId(UUID sourceEventId);
}