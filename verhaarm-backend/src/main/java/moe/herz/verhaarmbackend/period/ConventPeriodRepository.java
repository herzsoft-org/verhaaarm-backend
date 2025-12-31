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
	 * Find the "active" period for a given date (inclusive).
	 * Rule:
	 *   start_date <= d <= end_date
	 * Tie-breaker on overlap:
	 *   pick the one that started earlier (smallest start_date).
	 */
	@Query("""
		select p from ConventPeriodEntity p
		where p.startAt <= :d
		  and p.endAt >= :d
		order by p.startAt asc, p.endAt asc
	""")
	List<ConventPeriodEntity> findAllCoveringDateOrderEarlierStart(@Param("d") LocalDate d);

	/**
	 * For frontend date matching if you still want a "covering" query.
	 */
	default Optional<ConventPeriodEntity> findCovering(LocalDate d) {
		var list = findAllCoveringDateOrderEarlierStart(d);
		return list.isEmpty() ? Optional.empty() : Optional.of(list.getFirst());
	}
}
