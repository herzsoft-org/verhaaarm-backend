package moe.herz.verhaarmbackend.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Collection;
import java.time.OffsetDateTime;
import org.springframework.data.jpa.repository.Modifying;

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
        order by u.usernameNormalized asc
    """)
	List<UserEntity> findAllWithRolesOrdered();

	@Query("""
        select distinct u from UserEntity u
        left join fetch u.roles r
        where u.disabled = false
    """)
	List<UserEntity> findAllEnabledWithRoles();

	// Picker: only enabled users, minimal data, sorted.
	@Query("""
        select u from UserEntity u
        where u.disabled = false
          and (
            :qNorm = '' 
            or u.usernameNormalized like concat('%', :qNorm, '%')
            or lower(u.displayName) like concat('%', :qLower, '%')
          )
        order by u.usernameNormalized asc
    """)
	List<UserEntity> searchActiveForPicker(
			@Param("qNorm") String qNorm,
			@Param("qLower") String qLower
	);

	// Prevent lockout: last enabled ADMIN
	@Query("""
        select count(distinct u.id)
        from UserEntity u
        join u.roles r
        where u.disabled = false and r.role = moe.herz.verhaarmbackend.user.UserRole.ADMIN
    """)
	long countEnabledAdmins();

	@Query("""
        select count(distinct u.id)
        from UserEntity u
        join u.roles r
        where u.disabled = false and r.role = :role
    """)
	long countEnabledUsersWithRole(@Param("role") UserRole role);

	// Option A: role check without touching a possibly-detached entity
	@Query("""
        select count(r) > 0
        from UserEntity u
        join u.roles r
        where u.id = :userId and r.role = :role
    """)
	boolean hasRole(@Param("userId") UUID userId, @Param("role") UserRole role);

	Optional<UserEntity> findByUsername(String username);

	boolean existsByUsername(String username);

	boolean existsByUsernameNormalized(String usernameNormalized);

	@Query("""
    select u from UserEntity u
    where u.id in :ids
      and u.disabled = false
	""")
	List<UserEntity> findAllEnabledByIdIn(@Param("ids") Collection<UUID> ids);

	@Query("""
    select distinct u from UserEntity u
    left join fetch u.roles r
    where u.disabled = false
	""")
	List<UserEntity> findAllEnabledUsersWithRoles();

	@Query("""
    select distinct u from UserEntity u
    left join fetch u.roles r
    where u.lastOnlineAt >= :since
    order by u.lastOnlineAt desc nulls last, u.usernameNormalized asc
	""")
	List<UserEntity> findAllWithRolesOnlineSince(@Param("since") OffsetDateTime since);

	@Modifying
	@Query("""
    update UserEntity u
    set u.lastOnlineAt = :lastOnlineAt
    where u.id = :userId
	""")
	int updateLastOnlineAt(
			@Param("userId") UUID userId,
			@Param("lastOnlineAt") OffsetDateTime lastOnlineAt
	);
}
