package moe.herz.verhaarmbackend.periodprotocol;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ConventPeriodProtocolRepository extends JpaRepository<ConventPeriodProtocolEntity, UUID> {

	Optional<ConventPeriodProtocolEntity> findByPeriodId(UUID periodId);

	boolean existsByPeriodId(UUID periodId);
}