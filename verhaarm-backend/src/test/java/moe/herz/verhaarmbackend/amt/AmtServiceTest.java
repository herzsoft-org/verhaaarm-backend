package moe.herz.verhaarmbackend.amt;

import com.fasterxml.jackson.databind.ObjectMapper;
import moe.herz.verhaarmbackend.amt.dto.AemterOverviewDto;
import moe.herz.verhaarmbackend.amt.dto.AmtGroupLineDto;
import moe.herz.verhaarmbackend.audit.AuditLogRepository;
import moe.herz.verhaarmbackend.audit.AuditLogService;
import moe.herz.verhaarmbackend.user.UserEntity;
import moe.herz.verhaarmbackend.user.UserMemberStatus;
import moe.herz.verhaarmbackend.user.UserRepository;
import moe.herz.verhaarmbackend.user.UserRole;
import moe.herz.verhaarmbackend.user.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AmtServiceTest {

	private final AmtHolderRepository holders = mock(AmtHolderRepository.class);
	private final UserRepository users = mock(UserRepository.class);
	private final UserService userService = mock(UserService.class);
	private final AuditLogRepository auditRepo = mock(AuditLogRepository.class);
	private final AuditLogService audit = new AuditLogService(auditRepo, new ObjectMapper());
	private final AmtService service = new AmtService(holders, users, userService, audit);

	@Test
	void xxHolderWhoIsAlsoFechtwartMergesIntoOneLineAndFlagsFechtwartAsMerged() {
		UserEntity user = user(UserMemberStatus.BURSCH);

		when(holders.findAllWithUsers()).thenReturn(
				List.of(new AmtHolderEntity(UUID.randomUUID(), AmtType.XX, user))
		);
		when(users.findAllEnabledByRole(any())).thenReturn(List.of());
		when(users.findAllEnabledByRole(UserRole.FECHTWART)).thenReturn(List.of(user));

		AemterOverviewDto overview = service.getOverview();

		AmtGroupLineDto xx = overview.ehrengericht().stream()
				.filter(g -> g.amtType().equals(AmtType.XX.name()))
				.findFirst().orElseThrow();

		assertEquals(1, xx.lines().size());
		assertEquals("xx und Fechtwart", xx.lines().getFirst().displayTitle());
		assertEquals(1, xx.lines().getFirst().holders().size());
		assertEquals(user.getId(), xx.lines().getFirst().holders().getFirst().userId());

		// still present in the flat list (so it stays individually editable), but flagged
		// as merged so the UI can hide it there outside of edit mode.
		var fechtwart = overview.other().stream()
				.filter(o -> o.amtType().equals("FECHTWART"))
				.findFirst().orElseThrow();
		assertTrue(fechtwart.mergedIntoEhrengericht());
	}

	@Test
	void unassignedEhrengerichtSlotHasEmptyHolderLine() {
		when(holders.findAllWithUsers()).thenReturn(List.of());
		when(users.findAllEnabledByRole(any())).thenReturn(List.of());

		AemterOverviewDto overview = service.getOverview();

		AmtGroupLineDto x = overview.ehrengericht().stream()
				.filter(g -> g.amtType().equals(AmtType.X.name()))
				.findFirst().orElseThrow();

		assertEquals(1, x.lines().size());
		assertEquals("x", x.lines().getFirst().displayTitle());
		assertTrue(x.lines().getFirst().holders().isEmpty());
	}

	@Test
	void setHoldersRejectsActorWithNoAmt() {
		UserEntity actor = user(UserMemberStatus.BURSCH);
		when(users.hasRole(eq(actor.getId()), any())).thenReturn(false);
		when(holders.existsByUser_Id(actor.getId())).thenReturn(false);

		assertThrows(ResponseStatusException.class,
				() -> service.setHolders(AmtType.SCHRIFTWART, List.of(), actor));
	}

	@Test
	void setHoldersAllowedForAdminEvenWithoutHoldingAnyAmt() {
		UserEntity actor = user(UserMemberStatus.BURSCH);
		UserEntity newHolder = user(UserMemberStatus.BURSCH);
		when(users.hasRole(actor.getId(), UserRole.ADMIN)).thenReturn(true);
		when(users.findAllById(List.of(newHolder.getId()))).thenReturn(List.of(newHolder));
		when(holders.findByAmtType(AmtType.SCHRIFTWART)).thenReturn(List.of());

		var result = service.setHolders(AmtType.SCHRIFTWART, List.of(newHolder.getId()), actor);

		assertEquals(1, result.holders().size());
		verify(holders).deleteByAmtType(AmtType.SCHRIFTWART);
	}

	@Test
	void setHoldersAllowedForExistingAmtHolder() {
		UserEntity actor = user(UserMemberStatus.BURSCH);
		UserEntity newHolder = user(UserMemberStatus.BURSCH);
		when(users.hasRole(eq(actor.getId()), any())).thenReturn(false);
		when(holders.existsByUser_Id(actor.getId())).thenReturn(true);
		when(users.findAllById(List.of(newHolder.getId()))).thenReturn(List.of(newHolder));
		when(holders.findByAmtType(AmtType.SCHRIFTWART)).thenReturn(List.of());

		var result = service.setHolders(AmtType.SCHRIFTWART, List.of(newHolder.getId()), actor);

		assertEquals(1, result.holders().size());
		assertEquals(newHolder.getId(), result.holders().getFirst().userId());
		verify(holders).deleteByAmtType(AmtType.SCHRIFTWART);
		verify(holders).save(any(AmtHolderEntity.class));
	}

	@Test
	void setAutoHoldersDelegatesToUserServiceRoleReassignment() {
		UserEntity actor = user(UserMemberStatus.BURSCH);
		UserEntity newHolder = user(UserMemberStatus.BURSCH);
		List<UUID> ids = List.of(newHolder.getId());
		when(userService.setRoleHolders(UserRole.FECHTWART, ids, actor)).thenReturn(List.of(newHolder));

		var result = service.setAutoHolders(AutoAmt.FECHTWART, ids, actor);

		assertEquals("FECHTWART", result.amtType());
		assertTrue(result.autoFromRole());
		assertEquals(1, result.holders().size());
		assertEquals(newHolder.getId(), result.holders().getFirst().userId());
		verify(userService).setRoleHolders(UserRole.FECHTWART, ids, actor);
	}

	private static UserEntity user(UserMemberStatus status) {
		UUID id = UUID.randomUUID();
		UserEntity user = new UserEntity(id, "user-" + id, "User " + id, "hash", false);
		user.setMemberStatus(status);
		return user;
	}
}
