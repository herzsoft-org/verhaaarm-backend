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
}
