// src/main/java/moe/herz/verhaarmbackend/attendance/AttendanceRepository.java
package moe.herz.verhaarmbackend.attendance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AttendanceRepository extends JpaRepository<AttendanceEntity, UUID> {

	@Query("""
		select a from AttendanceEntity a
		where a.deletedAt is null
		  and a.eventId = :eventId
		order by a.createdAt asc
	""")
	List<AttendanceEntity> findVisibleByEventId(UUID eventId);

	@Query("""
		select a from AttendanceEntity a
		where a.deletedAt is null
		  and a.eventId = :eventId
		  and a.userId = :userId
	""")
	Optional<AttendanceEntity> findVisibleByEventAndUser(UUID eventId, UUID userId);

	// Includes soft-deleted rows (used to "revive" an exception instead of inserting a duplicate)
	@Query("""
		select a from AttendanceEntity a
		where a.eventId = :eventId
		  and a.userId = :userId
	""")
	Optional<AttendanceEntity> findAnyByEventAndUser(UUID eventId, UUID userId);

	/**
	 * Used by AttendanceService.generateFines():
	 * - Only visible (not deleted)
	 * - Only rows without fine_id
	 * - PESSIMISTIC_WRITE lock to prevent concurrent double-generation
	 * - Only exception statuses (LATE/ABSENT) should ever be stored, but we still filter defensively.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
		select a from AttendanceEntity a
		where a.deletedAt is null
		  and a.eventId = :eventId
		  and a.fineId is null
		  and a.status in (
		    moe.herz.verhaarmbackend.attendance.AttendanceStatus.LATE,
		    moe.herz.verhaarmbackend.attendance.AttendanceStatus.ABSENT
		  )
	""")
	List<AttendanceEntity> findVisibleByEventWithoutFineForUpdate(UUID eventId);
}
