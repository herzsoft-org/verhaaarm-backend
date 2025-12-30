package moe.herz.verhaarmbackend.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, UUID> {
	Optional<RefreshTokenEntity> findByTokenHash(String tokenHash);

	@Modifying
	@Query("delete from RefreshTokenEntity r where r.userId = :userId")
	int deleteAllForUser(@Param("userId") UUID userId);
}
