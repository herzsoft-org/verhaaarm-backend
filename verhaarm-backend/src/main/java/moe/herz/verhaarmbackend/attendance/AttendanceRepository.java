package moe.herz.verhaarmbackend.attendance;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AttendanceRepository extends JpaRepository<AttendanceEntity, UUID> {

	@Query("select a from AttendanceEntity a where a.eventId = :eventId and a.deletedAt is null order by a.createdAt asc")
	List<AttendanceEntity> findVisibleByEventId(UUID eventId);

	@Query("select a from AttendanceEntity a where a.eventId = :eventId and a.userId = :userId and a.deletedAt is null")
	Optional<AttendanceEntity> findVisibleByEventAndUser(UUID eventId, UUID userId);

	/**
	 * CRITICAL: Used by fine generation.
	 * Locks candidate rows so concurrent generate-fines calls cannot both create fines.
	 *
	 * Under Postgres READ COMMITTED, rows are re-checked after waiting on locks,
	 * so a second transaction will not see rows that were updated by the first one.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select a from AttendanceEntity a where a.eventId = :eventId and a.deletedAt is null and a.fineId is null")
	List<AttendanceEntity> findVisibleByEventWithoutFineForUpdate(UUID eventId);

	/**
	 * Non-locking variant (useful for list endpoints or diagnostics if needed).
	 */
	@Query("select a from AttendanceEntity a where a.eventId = :eventId and a.deletedAt is null and a.fineId is null")
	List<AttendanceEntity> findVisibleByEventWithoutFine(UUID eventId);
}
