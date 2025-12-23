package moe.herz.verhaarmbackend.fine;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FineRepository extends JpaRepository<FineEntity, UUID> {

	@Query("select f from FineEntity f where f.deletedAt is null order by f.createdAt desc")
	List<FineEntity> findAllVisible();

	@Query("select f from FineEntity f where f.deletedAt is null and :userId member of f.targetUserIds order by f.createdAt desc")
	List<FineEntity> findVisibleForTarget(UUID userId);

	@Query("select f from FineEntity f where f.id = :id and f.deletedAt is null")
	Optional<FineEntity> findVisibleById(UUID id);
}
