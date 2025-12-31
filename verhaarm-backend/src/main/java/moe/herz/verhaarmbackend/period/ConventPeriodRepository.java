package moe.herz.verhaarmbackend.period;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConventPeriodRepository extends JpaRepository<ConventPeriodEntity, UUID> {

	@Query("""
		select p from ConventPeriodEntity p
		order by p.semester desc, p.startAt desc
	""")
	List<ConventPeriodEntity> findAllOrdered();

	/**
	 * Find all periods covering a given date (inclusive) ordered by earliest start/end.
	 * startAt <= d <= endAt
	 */
	@Query("""
		select p from ConventPeriodEntity p
		where p.startAt <= :d
		  and p.endAt >= :d
		order by p.startAt asc, p.endAt asc
	""")
	List<ConventPeriodEntity> findAllCoveringDateOrderEarlierStart(@Param("d") LocalDate d);

	/**
	 * Convenience: the single "active" period for a date (first by the ordering above).
	 */
	default Optional<ConventPeriodEntity> findCovering(LocalDate d) {
		return findAllCoveringDateOrderEarlierStart(d).stream().findFirst();
	}
}
