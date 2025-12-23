package moe.herz.verhaarmbackend.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserRoleRepository extends JpaRepository<UserRoleEntity, UserRoleEntity.Pk> {

	@Query("select count(r) from UserRoleEntity r where r.role = ?1")
	long countByRole(UserRole role);
}
