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
	 * Find ALL periods covering a given date (inclusive), ordered by earliest start.
	 * Rule:
	 *   startAt <= d <= endAt
	 * Tie-breaker on overlap:
	 *   pick the one that started earlier (smallest startAt).
	 */
	@Query("""
		select p from ConventPeriodEntity p
		where p.startAt <= :d
		  and p.endAt >= :d
		order by p.startAt asc, p.endAt asc
	""")
	List<ConventPeriodEntity> findAllCoveringDateOrderEarlierStart(@Param("d") LocalDate d);

	/**
	 * Convenience: return the "best" covering period (earliest start) as Optional.
	 */
	default Optional<ConventPeriodEntity> findCovering(LocalDate d) {
		var list = findAllCoveringDateOrderEarlierStart(d);
		return list.isEmpty() ? Optional.empty() : Optional.of(list.getFirst());
	}
}
