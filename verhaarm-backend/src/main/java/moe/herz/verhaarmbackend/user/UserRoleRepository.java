package moe.herz.verhaarmbackend.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface UserRoleRepository extends JpaRepository<UserRoleEntity, UserRoleEntity.Pk> {

	@Query("select count(r) from UserRoleEntity r where r.role = ?1")
	long countByRole(UserRole role);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("delete from UserRoleEntity r where r.user.id = :userId")
	int deleteAllForUser(@Param("userId") UUID userId);
}
