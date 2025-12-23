package moe.herz.verhaarmbackend.user;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "user_roles")
@IdClass(UserRoleEntity.Pk.class)
public class UserRoleEntity {

	@Id
	@ManyToOne(optional = false, fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private UserEntity user;

	@Id
	@Enumerated(EnumType.STRING)
	@Column(name = "role", nullable = false)
	private UserRole role;

	protected UserRoleEntity() {
		// JPA
	}

	public UserRoleEntity(UserEntity user, UserRole role) {
		this.user = user;
		this.role = role;
	}

	public UserEntity getUser() { return user; }
	public UserRole getRole() { return role; }

	// Composite PK class
	public static final class Pk implements Serializable {
		private UUID user;
		private UserRole role;

		public Pk() {}

		public Pk(UUID user, UserRole role) {
			this.user = user;
			this.role = role;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (!(o instanceof Pk pk)) return false;
			return Objects.equals(user, pk.user) && role == pk.role;
		}

		@Override
		public int hashCode() {
			return Objects.hash(user, role);
		}
	}
}
