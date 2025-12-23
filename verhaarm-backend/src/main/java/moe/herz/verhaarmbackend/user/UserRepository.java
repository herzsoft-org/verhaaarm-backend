package moe.herz.verhaarmbackend.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {

	@Query("""
        select distinct u from UserEntity u
        left join fetch u.roles r
        where u.username = :username
    """)
	Optional<UserEntity> findByUsernameWithRoles(@Param("username") String username);

	@Query("""
        select distinct u from UserEntity u
        left join fetch u.roles r
        where u.id = :id
    """)
	Optional<UserEntity> findByIdWithRoles(@Param("id") UUID id);

	@Query("""
        select distinct u from UserEntity u
        left join fetch u.roles r
    """)
	List<UserEntity> findAllWithRoles();

	Optional<UserEntity> findByUsername(String username);

	boolean existsByUsername(String username);

	boolean existsByUsernameNormalized(String usernameNormalized);
}