package moe.herz.verhaarmbackend.finephoto;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FinePhotoRepository extends JpaRepository<FinePhotoEntity, UUID> {

	@Query("""
        select p from FinePhotoEntity p
        where p.fineId = :fineId and p.deletedAt is null
        order by p.createdAt asc
    """)
	List<FinePhotoEntity> findVisibleByFineId(UUID fineId);

	@Query("""
        select p from FinePhotoEntity p
        where p.id = :photoId and p.deletedAt is null
    """)
	Optional<FinePhotoEntity> findVisibleById(UUID photoId);

	@Query("""
        select p from FinePhotoEntity p
        where p.id = :photoId and p.fineId = :fineId and p.deletedAt is null
    """)
	Optional<FinePhotoEntity> findVisibleByIdAndFineId(UUID photoId, UUID fineId);
}
