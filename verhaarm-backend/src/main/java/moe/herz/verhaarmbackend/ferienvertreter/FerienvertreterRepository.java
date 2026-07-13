package moe.herz.verhaarmbackend.ferienvertreter;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface FerienvertreterRepository extends JpaRepository<FerienvertreterEntity, UUID> {

	@Query("""
        select f from FerienvertreterEntity f
        join fetch f.user u
        order by f.fromDate asc, f.untilDate asc
	""")
	List<FerienvertreterEntity> findAllOrderedWithUser();
}
