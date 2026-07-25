package moe.herz.verhaarmbackend.event;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventRepository extends JpaRepository<EventEntity, UUID> {

	@Query("select e from EventEntity e where e.deletedAt is null order by e.startsAt asc")
	List<EventEntity> findAllVisible();

	@Query("select e from EventEntity e where e.id = :id and e.deletedAt is null")
	Optional<EventEntity> findVisibleById(@Param("id") UUID id);

	@Query("""
		select e from EventEntity e
		where e.deletedAt is null
		  and e.startsAt <= :now
		  and e.startsAt > :cutoff
		order by e.startsAt asc
	""")
	List<EventEntity> findRecentlyStartedVisible(
			@Param("cutoff") OffsetDateTime cutoff,
			@Param("now") OffsetDateTime now
	);

	@Query("""
		select e from EventEntity e
		where e.deletedAt is null
		  and e.conventType is not null
		order by e.startsAt asc
	""")
	List<EventEntity> findAllConventsOrderedVisible();
}