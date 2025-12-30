package moe.herz.verhaarmbackend.task;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface TaskAssigneeRepository extends JpaRepository<TaskAssigneeEntity, TaskAssigneeEntity.Pk> {

	@Query("""
		select a.user.id
		from TaskAssigneeEntity a
		where a.task.id = :taskId
	""")
	List<UUID> listAssigneeUserIds(@Param("taskId") UUID taskId);

	@Modifying
	@Query("""
		delete from TaskAssigneeEntity a
		where a.task.id = :taskId
	""")
	int deleteAllForTask(@Param("taskId") UUID taskId);

	@Query("select a from TaskAssigneeEntity a join fetch a.task t where a.user.id = :userId")
	List<TaskAssigneeEntity> findAllByUserIdWithTask(@Param("userId") UUID userId);

	@org.springframework.data.jpa.repository.Modifying
	@Query("delete from TaskAssigneeEntity a where a.user.id = :userId and a.task.id = :taskId")
	int deleteOne(@Param("userId") UUID userId, @Param("taskId") UUID taskId);

	@Query("select count(a) from TaskAssigneeEntity a where a.task.id = :taskId")
	long countAssignees(@Param("taskId") UUID taskId);

}
