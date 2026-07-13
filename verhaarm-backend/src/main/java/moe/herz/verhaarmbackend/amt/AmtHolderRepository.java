package moe.herz.verhaarmbackend.amt;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface AmtHolderRepository extends JpaRepository<AmtHolderEntity, UUID> {

	@Query("""
        select h from AmtHolderEntity h
        join fetch h.user u
        order by h.amtType asc, u.usernameNormalized asc
	""")
	List<AmtHolderEntity> findAllWithUsers();

	List<AmtHolderEntity> findByAmtType(AmtType amtType);

	boolean existsByUser_Id(UUID userId);

	void deleteByAmtType(AmtType amtType);
}
