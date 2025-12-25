package moe.herz.verhaarmbackend.period;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConventPeriodRepository extends JpaRepository<ConventPeriodEntity, UUID> {

	@Query("select p from ConventPeriodEntity p where p.active = true")
	Optional<ConventPeriodEntity> findActive();

	@Query("""
		select p from ConventPeriodEntity p
		order by p.semester desc, p.startAt desc
	""")
	List<ConventPeriodEntity> findAllOrdered();

	/**
	 * Find the period that covers the given timestamp.
	 * Coverage rule: start_at <= ts < end_at
	 */
	@Query("""
		select p from ConventPeriodEntity p
		where p.startAt <= :ts
		  and p.endAt > :ts
		order by p.startAt desc
	""")
	Optional<ConventPeriodEntity> findCovering(@Param("ts") OffsetDateTime ts);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
		update ConventPeriodEntity p
		set p.active = false
		where p.active = true
		  and p.id <> :id
	""")
	int deactivateAllExcept(@Param("id") UUID id);
}
