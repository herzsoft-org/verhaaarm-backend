package moe.herz.verhaarmbackend.finecatalog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface FineCatalogRepository extends JpaRepository<FineCatalogItemEntity, UUID> {

	@Query("select c from FineCatalogItemEntity c where c.deletedAt is null order by c.title asc")
	List<FineCatalogItemEntity> findAllVisible();

	@Query("select c from FineCatalogItemEntity c where c.deletedAt is null and c.active = true order by c.title asc")
	List<FineCatalogItemEntity> findAllActiveVisible();
}
