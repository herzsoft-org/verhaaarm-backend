package moe.herz.verhaarmbackend.task;

import jakarta.persistence.*;
import moe.herz.verhaarmbackend.user.UserEntity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "task_assignees")
@IdClass(TaskAssigneeEntity.Pk.class)
public class TaskAssigneeEntity {

	@Id
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "task_id", nullable = false)
	private TaskEntity task;

	@Id
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private UserEntity user;

	protected TaskAssigneeEntity() {
		// JPA
	}

	public TaskAssigneeEntity(TaskEntity task, UserEntity user) {
		this.task = task;
		this.user = user;
	}

	public TaskEntity getTask() { return task; }
	public UserEntity getUser() { return user; }

	public static final class Pk implements Serializable {
		private UUID task;
		private UUID user;

		public Pk() {}

		public Pk(UUID task, UUID user) {
			this.task = task;
			this.user = user;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (!(o instanceof Pk pk)) return false;
			return Objects.equals(task, pk.task) && Objects.equals(user, pk.user);
		}

		@Override
		public int hashCode() {
			return Objects.hash(task, user);
		}
	}
}
