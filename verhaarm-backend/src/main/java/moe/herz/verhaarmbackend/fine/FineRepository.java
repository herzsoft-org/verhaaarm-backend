package moe.herz.verhaarmbackend.fine;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FineRepository extends JpaRepository<FineEntity, UUID> {

	@Query("select f from FineEntity f where f.deletedAt is null order by f.createdAt desc")
	List<FineEntity> findAllVisible();

	@Query("select f from FineEntity f where f.deletedAt is null and :userId member of f.targetUserIds order by f.createdAt desc")
	List<FineEntity> findVisibleForTarget(UUID userId);

	@Query("select f from FineEntity f where f.id = :id and f.deletedAt is null")
	Optional<FineEntity> findVisibleById(UUID id);

	// -------- BALANCE (sum of non-deleted fines where user is a target) --------

	@Query("""
        select coalesce(sum(f.amountCents), 0)
        from FineEntity f
        where f.deletedAt is null
          and :userId member of f.targetUserIds
    """)
	long sumVisibleAmountCentsForTarget(UUID userId);

	@Query("""
        select coalesce(sum(f.amountCents), 0)
        from FineEntity f
        where f.deletedAt is null
          and f.periodId = :periodId
          and :userId member of f.targetUserIds
    """)
	long sumVisibleAmountCentsForTargetInPeriod(UUID userId, UUID periodId);

	// -------- CSV export (avoid N+1 for targets) --------

	@Query("""
        select distinct f from FineEntity f
        left join fetch f.targetUserIds
        where f.deletedAt is null
        order by f.createdAt desc
    """)
	List<FineEntity> findAllVisibleWithTargets();

	@Query("""
        select distinct f from FineEntity f
        left join fetch f.targetUserIds
        where f.deletedAt is null
          and f.periodId = :periodId
        order by f.createdAt desc
    """)
	List<FineEntity> findVisibleByPeriodWithTargets(UUID periodId);

	@Query("""
        select distinct f from FineEntity f
        left join fetch f.targetUserIds
        order by f.createdAt desc
    """)
	List<FineEntity> findAllIncludingDeletedWithTargets();

	@Query("""
        select distinct f from FineEntity f
        left join fetch f.targetUserIds
        where f.periodId = :periodId
        order by f.createdAt desc
    """)
	List<FineEntity> findAllIncludingDeletedByPeriodWithTargets(UUID periodId);
}
