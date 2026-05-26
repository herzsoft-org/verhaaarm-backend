package moe.herz.verhaarmbackend.paukstunde;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface PaukstundeRepository extends JpaRepository<PaukstundeEntity, UUID> {

	@Query("""
		select distinct p from PaukstundeEntity p
		left join fetch p.participantUserIds
		where p.date >= :fromDate
		  and p.date <= :toDate
		order by p.date desc, p.createdAt desc
	""")
	List<PaukstundeEntity> findInDateRangeWithParticipants(
			@Param("fromDate") LocalDate fromDate,
			@Param("toDate") LocalDate toDate
	);

	@Query("""
		select distinct p from PaukstundeEntity p
		left join fetch p.participantUserIds
		where p.date >= :fromDate
		  and p.date <= :toDate
		  and :userId member of p.participantUserIds
		order by p.date desc, p.createdAt desc
	""")
	List<PaukstundeEntity> findForParticipantInDateRange(
			@Param("userId") UUID userId,
			@Param("fromDate") LocalDate fromDate,
			@Param("toDate") LocalDate toDate
	);

	@Modifying
	@Query(value = "delete from paukstunde_participants where user_id = :userId", nativeQuery = true)
	int deleteParticipantsForUser(@Param("userId") UUID userId);

	@Modifying
	@Query("delete from PaukstundeEntity p where p.createdByUserId = :userId")
	int deleteCreatedByUser(@Param("userId") UUID userId);

	@Query(value = """
		select p.id
		from paukstunden p
		left join paukstunde_participants pp on pp.paukstunde_id = p.id
		where pp.paukstunde_id is null
	""", nativeQuery = true)
	List<UUID> findIdsWithNoParticipants();
}
