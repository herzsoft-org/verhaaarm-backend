package moe.herz.verhaarmbackend.finecatalog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FineCatalogRepository extends JpaRepository<FineCatalogItemEntity, UUID> {

	// System catalog items (attendance automation)
	UUID SYS_LATE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
	UUID SYS_ABSENT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

	@Query("select c from FineCatalogItemEntity c where c.deletedAt is null order by c.title asc")
	List<FineCatalogItemEntity> findAllVisible();

	@Query("select c from FineCatalogItemEntity c where c.deletedAt is null and c.active = true order by c.title asc")
	List<FineCatalogItemEntity> findAllActiveVisible();

	@Query("select c from FineCatalogItemEntity c where c.id = :id and c.deletedAt is null and c.active = true")
	Optional<FineCatalogItemEntity> findActiveVisibleById(UUID id);

	// ---- Manual fine creation: exclude system items ----

	@Query("""
		select c from FineCatalogItemEntity c
		where c.deletedAt is null
		  and c.active = true
		  and c.id not in (
		    moe.herz.verhaarmbackend.finecatalog.FineCatalogRepository.SYS_LATE_ID,
		    moe.herz.verhaarmbackend.finecatalog.FineCatalogRepository.SYS_ABSENT_ID
		  )
		order by c.title asc
	""")
	List<FineCatalogItemEntity> findAllActiveVisibleForManualCreation();

	@Query("""
		select c from FineCatalogItemEntity c
		where c.deletedAt is null
		  and c.id not in (
		    moe.herz.verhaarmbackend.finecatalog.FineCatalogRepository.SYS_LATE_ID,
		    moe.herz.verhaarmbackend.finecatalog.FineCatalogRepository.SYS_ABSENT_ID
		  )
		order by c.title asc
	""")
	List<FineCatalogItemEntity> findAllVisibleForManualCreation();
}
