package moe.herz.verhaarmbackend.period;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConventPeriodRepository extends JpaRepository<ConventPeriodEntity, UUID> {

	boolean existsBySemester(String semester);

	@Query("select p from ConventPeriodEntity p where p.active = true")
	Optional<ConventPeriodEntity> findActive();

	@Query("select p from ConventPeriodEntity p order by p.startAt desc")
	List<ConventPeriodEntity> findAllOrdered();

	@Modifying
	@Query("update ConventPeriodEntity p set p.active = false where p.active = true and p.id <> :keepId")
	int deactivateAllExcept(UUID keepId);
}
