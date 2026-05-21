package moe.herz.verhaarmbackend.settings;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserSettingRepository extends JpaRepository<UserSettingEntity, UUID> {

	List<UserSettingEntity> findAllByUserId(UUID userId);

	Optional<UserSettingEntity> findByUserIdAndKey(UUID userId, String key);
}