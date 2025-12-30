package moe.herz.verhaarmbackend.push;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PushDeviceRepository extends JpaRepository<PushDeviceEntity, UUID> {

	@Query("""
		select d from PushDeviceEntity d
		where d.userId = :userId
	""")
	List<PushDeviceEntity> findAllForUser(@Param("userId") UUID userId);

	@Query("""
		select d from PushDeviceEntity d
		where d.kind = 'WEBPUSH'
		  and d.endpoint = :endpoint
	""")
	Optional<PushDeviceEntity> findWebPushByEndpoint(@Param("endpoint") String endpoint);

	@Query("""
		select d from PushDeviceEntity d
		where d.kind = 'FCM'
		  and d.fcmToken = :token
	""")
	Optional<PushDeviceEntity> findFcmByToken(@Param("token") String token);

	@Modifying
	@Query("delete from PushDeviceEntity d where d.userId = :userId")
	int deleteAllForUser(@Param("userId") UUID userId);
}
