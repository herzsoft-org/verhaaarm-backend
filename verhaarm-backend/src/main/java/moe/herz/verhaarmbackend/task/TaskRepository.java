package moe.herz.verhaarmbackend.task;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskRepository extends JpaRepository<TaskEntity, UUID> {

	@Query("""
		select distinct t
		from TaskEntity t
		join t.assignees filterA
		left join fetch t.assignees a
		left join fetch a.user u
		where t.deletedAt is null
		  and filterA.user.id = :userId
		order by t.solved asc, t.createdAt desc
	""")
	List<TaskEntity> findVisibleForUser(@Param("userId") UUID userId);

	@Query("""
		select distinct t
		from TaskEntity t
		left join fetch t.assignees a
		left join fetch a.user u
		where t.deletedAt is null
		order by t.createdAt desc
	""")
	List<TaskEntity> findAllVisibleWithAssignees();

	@Query("""
		select distinct t
		from TaskEntity t
		left join fetch t.assignees a
		left join fetch a.user u
		where t.id = :id
		  and t.deletedAt is null
	""")
	Optional<TaskEntity> findVisibleByIdWithAssignees(@Param("id") UUID id);

	@Query("""
		select count(a) > 0
		from TaskAssigneeEntity a
		where a.task.id = :taskId
		  and a.user.id = :userId
	""")
	boolean isAssignee(@Param("taskId") UUID taskId, @Param("userId") UUID userId);

	@Modifying
	@Query("""
		update TaskEntity t
		set t.deletedAt = :now
		where t.deletedAt is null
		  and t.solved = true
		  and t.id in (
		    select a.task.id
		    from TaskAssigneeEntity a
		    where a.user.id = :userId
		  )
	""")
	int softDeleteSolvedAssignedToUser(@Param("userId") UUID userId, @Param("now") OffsetDateTime now);
}
