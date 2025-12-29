package moe.herz.verhaarmbackend.notification;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<NotificationEntity, UUID> {

	@Query("""
		select n from NotificationEntity n
		where n.userId = :userId
		  and n.deletedAt is null
		order by n.createdAt desc
	""")
	List<NotificationEntity> findVisibleForUser(@Param("userId") UUID userId, Pageable pageable);

	@Query("""
		select n from NotificationEntity n
		where n.userId = :userId
		  and n.deletedAt is null
		  and n.id = :id
	""")
	Optional<NotificationEntity> findVisibleByIdForUser(@Param("userId") UUID userId, @Param("id") UUID id);

	@Query("""
		select count(n) from NotificationEntity n
		where n.userId = :userId
		  and n.deletedAt is null
		  and n.readAt is null
	""")
	long countUnread(@Param("userId") UUID userId);

	@Query("""
		update NotificationEntity n
		set n.deletedAt = CURRENT_TIMESTAMP
		where n.userId = :userId
		  and n.deletedAt is null
	""")
	@org.springframework.data.jpa.repository.Modifying
	int softDeleteAllForUser(@Param("userId") UUID userId);

	@Query("""
		update NotificationEntity n
		set n.deletedAt = CURRENT_TIMESTAMP
		where n.userId = :userId
		  and n.id = :id
		  and n.deletedAt is null
	""")
	@org.springframework.data.jpa.repository.Modifying
	int softDeleteOneForUser(@Param("userId") UUID userId, @Param("id") UUID id);

	@Query("""
		update NotificationEntity n
		set n.readAt = CURRENT_TIMESTAMP
		where n.userId = :userId
		  and n.id = :id
		  and n.deletedAt is null
		  and n.readAt is null
	""")
	@org.springframework.data.jpa.repository.Modifying
	int markRead(@Param("userId") UUID userId, @Param("id") UUID id);
}
