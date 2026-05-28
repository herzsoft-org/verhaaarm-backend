package moe.herz.verhaarmbackend.liveevent;

import moe.herz.verhaarmbackend.user.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LiveEventReactionRepository extends JpaRepository<LiveEventReactionEntity, UUID> {

	Optional<LiveEventReactionEntity> findByLiveEventIdAndUserIdAndType(
			UUID liveEventId,
			UUID userId,
			LiveEventReactionType type
	);

	long countByLiveEventIdAndType(UUID liveEventId, LiveEventReactionType type);

	boolean existsByLiveEventIdAndUserIdAndType(UUID liveEventId, UUID userId, LiveEventReactionType type);

	@Query("""
		select u from UserEntity u
		where u.id in (
			select r.userId from LiveEventReactionEntity r
			where r.liveEventId = :liveEventId
			  and r.type = :type
		)
		order by u.displayName asc
	""")
	List<UserEntity> findUsersByLiveEventIdAndType(
			@Param("liveEventId") UUID liveEventId,
			@Param("type") LiveEventReactionType type
	);
}
