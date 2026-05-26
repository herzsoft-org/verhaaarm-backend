package moe.herz.verhaarmbackend.paukstunde;

import com.fasterxml.jackson.databind.ObjectMapper;
import moe.herz.verhaarmbackend.audit.AuditLogRepository;
import moe.herz.verhaarmbackend.audit.AuditLogService;
import moe.herz.verhaarmbackend.common.ApiValidationException;
import moe.herz.verhaarmbackend.paukstunde.dto.CreatePaukstundeRequest;
import moe.herz.verhaarmbackend.paukstunde.dto.UpdatePaukstundeRequest;
import moe.herz.verhaarmbackend.period.ConventPeriodEntity;
import moe.herz.verhaarmbackend.period.ConventPeriodRepository;
import moe.herz.verhaarmbackend.user.UserEntity;
import moe.herz.verhaarmbackend.user.UserMemberStatus;
import moe.herz.verhaarmbackend.user.UserRepository;
import moe.herz.verhaarmbackend.user.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaukstundeServiceTest {

	private final PaukstundeRepository paukstunden = mock(PaukstundeRepository.class);
	private final UserRepository users = mock(UserRepository.class);
	private final ConventPeriodRepository periods = mock(ConventPeriodRepository.class);
	private final AuditLogRepository auditRepo = mock(AuditLogRepository.class);
	private final AuditLogService audit = new AuditLogService(auditRepo, new ObjectMapper());
	private final PaukstundeService service = new PaukstundeService(paukstunden, users, periods, audit);

	@Test
	void linkedUserCanSeeCurrentConventsperiodeSessions() {
		UserEntity participant = user(UserMemberStatus.BURSCH);
		PaukstundeEntity session = session(user(UserMemberStatus.BURSCH).getId(), participant.getId());
		LocalDate from = LocalDate.now().minusDays(1);
		LocalDate to = LocalDate.now().plusDays(1);
		when(periods.findCovering(any())).thenReturn(Optional.of(new ConventPeriodEntity(UUID.randomUUID(), "SS26", from, to, false)));
		when(paukstunden.findForParticipantInDateRange(participant.getId(), from, to)).thenReturn(List.of(session));
		when(users.findAllById(anySet())).thenReturn(List.of(participant));

		var result = service.listCurrentConventsperiode(participant);

		assertEquals(1, result.size());
		assertEquals(session.getId(), result.getFirst().id());
		verify(paukstunden).findForParticipantInDateRange(participant.getId(), from, to);
		verify(paukstunden, never()).findInDateRangeWithParticipants(any(), any());
	}

	@Test
	void linkedUserCanUpdateSession() {
		UserEntity participant = user(UserMemberStatus.BURSCH);
		UserEntity creator = user(UserMemberStatus.BURSCH);
		PaukstundeEntity session = session(creator.getId(), participant.getId());
		when(paukstunden.findById(session.getId())).thenReturn(Optional.of(session));
		when(users.findAllEnabledByIdIn(Set.of(participant.getId()))).thenReturn(List.of(participant));
		when(users.findAllById(anySet())).thenReturn(List.of(participant, creator));

		assertDoesNotThrow(() -> service.update(
				session.getId(),
				new UpdatePaukstundeRequest(null, 2, null),
				participant
		));
		verify(paukstunden).save(session);
	}

	@Test
	void linkedUserCanDeleteSession() {
		UserEntity participant = user(UserMemberStatus.BURSCH);
		PaukstundeEntity session = session(user(UserMemberStatus.BURSCH).getId(), participant.getId());
		when(paukstunden.findById(session.getId())).thenReturn(Optional.of(session));

		assertDoesNotThrow(() -> service.delete(session.getId(), participant));
		verify(paukstunden).delete(session);
	}

	@Test
	void unlinkedUserCannotUpdateOrDeleteSession() {
		UserEntity unlinked = user(UserMemberStatus.BURSCH);
		PaukstundeEntity session = session(user(UserMemberStatus.BURSCH).getId(), user(UserMemberStatus.BURSCH).getId());
		when(paukstunden.findById(session.getId())).thenReturn(Optional.of(session));

		ResponseStatusException updateEx = assertThrows(ResponseStatusException.class, () -> service.update(
				session.getId(),
				new UpdatePaukstundeRequest(null, 2, null),
				unlinked
		));
		ResponseStatusException deleteEx = assertThrows(ResponseStatusException.class, () -> service.delete(session.getId(), unlinked));
		assertEquals(HttpStatus.FORBIDDEN, updateEx.getStatusCode());
		assertEquals(HttpStatus.FORBIDDEN, deleteEx.getStatusCode());
		verify(paukstunden, never()).save(any());
		verify(paukstunden, never()).delete(any());
	}

	@Test
	void adminAndFechtwartCanUpdateUnlinkedSessions() {
		UserEntity admin = user(UserMemberStatus.BURSCH, UserRole.ADMIN);
		UserEntity fechtwart = user(UserMemberStatus.BURSCH, UserRole.FECHTWART);
		UserEntity participant = user(UserMemberStatus.BURSCH);
		PaukstundeEntity adminSession = session(user(UserMemberStatus.BURSCH).getId(), participant.getId());
		PaukstundeEntity fechtwartSession = session(user(UserMemberStatus.BURSCH).getId(), participant.getId());

		when(users.findAllEnabledByIdIn(Set.of(participant.getId()))).thenReturn(List.of(participant));
		when(users.findAllById(anySet())).thenReturn(List.of(participant));
		when(paukstunden.findById(adminSession.getId())).thenReturn(Optional.of(adminSession));
		when(paukstunden.findById(fechtwartSession.getId())).thenReturn(Optional.of(fechtwartSession));

		assertDoesNotThrow(() -> service.update(adminSession.getId(), new UpdatePaukstundeRequest(null, 2, null), admin));
		assertDoesNotThrow(() -> service.update(fechtwartSession.getId(), new UpdatePaukstundeRequest(null, 2, null), fechtwart));
		verify(paukstunden).save(adminSession);
		verify(paukstunden).save(fechtwartSession);
	}

	@Test
	void adminAndFechtwartCanDeleteUnlinkedSessions() {
		UserEntity admin = user(UserMemberStatus.BURSCH, UserRole.ADMIN);
		UserEntity fechtwart = user(UserMemberStatus.BURSCH, UserRole.FECHTWART);
		PaukstundeEntity adminSession = session(user(UserMemberStatus.BURSCH).getId(), user(UserMemberStatus.BURSCH).getId());
		PaukstundeEntity fechtwartSession = session(user(UserMemberStatus.BURSCH).getId(), user(UserMemberStatus.BURSCH).getId());
		when(paukstunden.findById(adminSession.getId())).thenReturn(Optional.of(adminSession));
		when(paukstunden.findById(fechtwartSession.getId())).thenReturn(Optional.of(fechtwartSession));

		assertDoesNotThrow(() -> service.delete(adminSession.getId(), admin));
		assertDoesNotThrow(() -> service.delete(fechtwartSession.getId(), fechtwart));
		verify(paukstunden).delete(adminSession);
		verify(paukstunden).delete(fechtwartSession);
	}

	@Test
	void rejectsUpdateWithLessThanOneHour() {
		UserEntity participant = user(UserMemberStatus.BURSCH);
		PaukstundeEntity session = session(user(UserMemberStatus.BURSCH).getId(), participant.getId());
		when(paukstunden.findById(session.getId())).thenReturn(Optional.of(session));
		when(users.findAllEnabledByIdIn(Set.of(participant.getId()))).thenReturn(List.of(participant));

		ApiValidationException ex = assertThrows(ApiValidationException.class, () -> service.update(
				session.getId(),
				new UpdatePaukstundeRequest(null, 0, null),
				participant
		));

		assertEquals("PAUKSTUNDE_HOURS_INVALID", ex.getCode());
		verify(paukstunden, never()).save(any());
	}

	@Test
	void rejectsCreateWithLessThanOneHour() {
		UserEntity actor = user(UserMemberStatus.BURSCH);
		when(users.findAllEnabledByIdIn(Set.of(actor.getId()))).thenReturn(List.of(actor));

			ApiValidationException ex = assertThrows(ApiValidationException.class, () -> service.create(
				new CreatePaukstundeRequest(LocalDate.now(), 0, Set.of(actor.getId())),
				actor
		));

		assertEquals("PAUKSTUNDE_HOURS_INVALID", ex.getCode());
		verify(paukstunden, never()).save(any());
	}

	@Test
	void userWithNoPaukstundenKeepsZeroTotal() {
		UserEntity actor = user(UserMemberStatus.BURSCH);
		LocalDate from = LocalDate.now().minusDays(1);
		LocalDate to = LocalDate.now().plusDays(1);
		when(users.findById(actor.getId())).thenReturn(Optional.of(actor));
		when(periods.findCovering(any())).thenReturn(Optional.of(new ConventPeriodEntity(UUID.randomUUID(), "SS26", from, to, false)));
		when(paukstunden.findForParticipantInDateRange(actor.getId(), from, to)).thenReturn(List.of());

		var total = service.userCurrentTotal(actor.getId(), actor);

		assertEquals(0, total.totalHours());
		assertEquals(0, total.entryCount());
	}

	private static PaukstundeEntity session(UUID creatorId, UUID... participantIds) {
		PaukstundeEntity session = new PaukstundeEntity(UUID.randomUUID(), LocalDate.now(), 1, creatorId);
		for (UUID participantId : participantIds) session.addParticipant(participantId);
		return session;
	}

	private static UserEntity user(UserMemberStatus status, UserRole... roles) {
		UUID id = UUID.randomUUID();
		UserEntity user = new UserEntity(id, "user-" + id, "User " + id, "hash", false);
		user.setMemberStatus(status);
		for (UserRole role : roles) user.addRole(role);
		return user;
	}
}
