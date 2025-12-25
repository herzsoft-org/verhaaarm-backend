package moe.herz.verhaarmbackend.fine;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FineRepository extends JpaRepository<FineEntity, UUID> {

	// --------------------
	// "Visible" = not soft-deleted
	// --------------------

	@Query("""
		select f from FineEntity f
		where f.deletedAt is null
		order by f.fineDate desc, f.createdAt desc
	""")
	List<FineEntity> findAllVisible();

	@Query("""
		select f from FineEntity f
		where f.deletedAt is null
		  and f.id = :id
	""")
	Optional<FineEntity> findVisibleById(@Param("id") UUID id);

	@Query("""
		select distinct f from FineEntity f
		join f.targetUserIds t
		where f.deletedAt is null
		  and t = :targetUserId
		order by f.fineDate desc, f.createdAt desc
	""")
	List<FineEntity> findVisibleForTarget(@Param("targetUserId") UUID targetUserId);

	// --------------------
	// BALANCE (period-derived)
	// --------------------

	@Query(value = """
		select coalesce(sum(f.amount_cents), 0)
		from fines f
		join fine_targets t on t.fine_id = f.id
		join convent_periods p on p.id = :periodId
		where f.deleted_at is null
		  and t.user_id = :targetUserId
		  and f.fine_date >= (p.start_at at time zone 'UTC')::date
		  and f.fine_date <  (p.end_at   at time zone 'UTC')::date
	""", nativeQuery = true)
	long sumVisibleAmountCentsForTargetInPeriod(
			@Param("targetUserId") UUID targetUserId,
			@Param("periodId") UUID periodId
	);

	// --------------------
	// EXPORT (date range, fetch targets to avoid N+1)
	// Range semantics: fromDate <= fineDate < toDate
	// --------------------

	@Query("""
		select distinct f from FineEntity f
		left join fetch f.targetUserIds
		where f.deletedAt is null
		  and f.fineDate >= :fromDate
		  and f.fineDate <  :toDate
		order by f.fineDate desc, f.createdAt desc
	""")
	List<FineEntity> findVisibleInDateRangeWithTargets(
			@Param("fromDate") LocalDate fromDate,
			@Param("toDate") LocalDate toDate
	);

	@Query("""
		select distinct f from FineEntity f
		left join fetch f.targetUserIds
		where f.fineDate >= :fromDate
		  and f.fineDate <  :toDate
		order by f.fineDate desc, f.createdAt desc
	""")
	List<FineEntity> findAllIncludingDeletedInDateRangeWithTargets(
			@Param("fromDate") LocalDate fromDate,
			@Param("toDate") LocalDate toDate
	);
}
