package moe.herz.verhaarmbackend.periodprotocol;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConventPeriodProtocolRepository extends JpaRepository<ConventPeriodProtocolEntity, UUID> {

	Optional<ConventPeriodProtocolEntity> findByPeriodId(UUID periodId);

	boolean existsByPeriodId(UUID periodId);

	@Query("select p.periodId from ConventPeriodProtocolEntity p")
	List<UUID> findAllPeriodIds();
}