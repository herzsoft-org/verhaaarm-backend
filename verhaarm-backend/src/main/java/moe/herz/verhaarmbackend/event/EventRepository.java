package moe.herz.verhaarmbackend.event;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

	/**
	 * Same as {@link #findAllConventsOrderedVisible()} but takes a pessimistic write lock on every
	 * row, for the Convente board's batch commit only (never for GET/dry-run, which must stay
	 * non-blocking). Under READ COMMITTED, two concurrent board saves could otherwise both validate
	 * against the same pre-write timeline and commit a combined result neither of them actually
	 * checked; this serializes concurrent board writes against each other instead.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
		select e from EventEntity e
		where e.deletedAt is null
		  and e.conventType is not null
		order by e.startsAt asc
	""")
	List<EventEntity> findAllConventsOrderedVisibleForUpdate();
}