package moe.herz.verhaarmbackend.attendance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;

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

	@Query("""
		select a from AttendanceEntity a
		where a.eventId = :eventId
		  and a.userId = :userId
	""")
	Optional<AttendanceEntity> findAnyByEventAndUser(UUID eventId, UUID userId);

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

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
	  select a from AttendanceEntity a
	  where a.eventId = :eventId
	    and a.deletedAt is null
	""")
	List<AttendanceEntity> findVisibleByEventForUpdate(@Param("eventId") UUID eventId);

	@Query("""
		select distinct a.fineId from AttendanceEntity a
		where a.userId = :userId
		  and a.fineId is not null
	""")
	List<UUID> findFineIdsForUser(@Param("userId") UUID userId);

	@Modifying
	@Query("delete from AttendanceEntity a where a.userId = :userId")
	int hardDeleteAllForUser(@Param("userId") UUID userId);
}
