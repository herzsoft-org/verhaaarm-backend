package moe.herz.verhaarmbackend.session;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface UserSessionRepository extends JpaRepository<UserSessionEntity, UUID> {

	@Query("""
        select s from UserSessionEntity s
        where s.userId = :userId
        order by s.lastActiveAt desc
    """)
	List<UserSessionEntity> findAllForUser(@Param("userId") UUID userId);

	@Query("""
        select s from UserSessionEntity s
        where s.revokedAt is null
          and s.lastActiveAt >= :since
          and (s.expiresAt is null or s.expiresAt > :now)
    """)
	List<UserSessionEntity> findActiveSince(
			@Param("since") OffsetDateTime since,
			@Param("now") OffsetDateTime now
	);
}