package moe.herz.verhaarmbackend.fine;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

	// Conventsperioden are derived (see ConventDerivation), not stored, so the caller resolves
	// the period to a concrete date range before calling this.
	@Query(value = """
    select coalesce(sum(f.amount_cents), 0)
    from fines f
    join fine_targets t on t.fine_id = f.id
    where f.deleted_at is null
      and t.user_id = :targetUserId
      and f.fine_date >= :fromDate
      and f.fine_date <= :toDate
""", nativeQuery = true)
	long sumVisibleAmountCentsForTargetInPeriod(
			@Param("targetUserId") UUID targetUserId,
			@Param("fromDate") LocalDate fromDate,
			@Param("toDate") LocalDate toDate
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

	// --------------------
	// HARD DELETE HELPERS
	// --------------------

	@Modifying
	@Query(value = "delete from fine_targets where user_id = :userId", nativeQuery = true)
	int deleteTargetsForUser(@Param("userId") UUID userId);

	@Query(value = """
		select f.id
		from fines f
		left join fine_targets t on t.fine_id = f.id
		where t.fine_id is null
	""", nativeQuery = true)
	List<UUID> findFineIdsWithNoTargets();
}
