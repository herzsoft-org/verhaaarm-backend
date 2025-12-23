package moe.herz.verhaarmbackend.attendance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AttendanceRepository extends JpaRepository<AttendanceEntity, UUID> {

	@Query("select a from AttendanceEntity a where a.eventId = :eventId and a.deletedAt is null order by a.createdAt asc")
	List<AttendanceEntity> findVisibleByEventId(UUID eventId);

	@Query("select a from AttendanceEntity a where a.eventId = :eventId and a.userId = :userId and a.deletedAt is null")
	Optional<AttendanceEntity> findVisibleByEventAndUser(UUID eventId, UUID userId);

	@Query("select a from AttendanceEntity a where a.eventId = :eventId and a.deletedAt is null and a.fineId is null")
	List<AttendanceEntity> findVisibleByEventWithoutFine(UUID eventId);
}
