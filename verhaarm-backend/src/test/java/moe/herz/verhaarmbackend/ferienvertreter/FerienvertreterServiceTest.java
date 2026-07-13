package moe.herz.verhaarmbackend.ferienvertreter;

import com.fasterxml.jackson.databind.ObjectMapper;
import moe.herz.verhaarmbackend.amt.AmtService;
import moe.herz.verhaarmbackend.audit.AuditLogRepository;
import moe.herz.verhaarmbackend.audit.AuditLogService;
import moe.herz.verhaarmbackend.ferienvertreter.dto.CreateFerienvertreterRequest;
import moe.herz.verhaarmbackend.ferienvertreter.dto.UpdateFerienvertreterRequest;
import moe.herz.verhaarmbackend.user.UserEntity;
import moe.herz.verhaarmbackend.user.UserMemberStatus;
import moe.herz.verhaarmbackend.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FerienvertreterServiceTest {

	private final FerienvertreterRepository ferienvertreter = mock(FerienvertreterRepository.class);
	private final UserRepository users = mock(UserRepository.class);
	private final AmtService amt = mock(AmtService.class);
	private final AuditLogRepository auditRepo = mock(AuditLogRepository.class);
	private final AuditLogService audit = new AuditLogService(auditRepo, new ObjectMapper());
	private final FerienvertreterService service = new FerienvertreterService(ferienvertreter, users, amt, audit);

	@Test
	void createRejectsActorWithoutAnyAmt() {
		UserEntity actor = user();
		when(amt.isAmtHolder(actor)).thenReturn(false);

		var req = new CreateFerienvertreterRequest(UUID.randomUUID(), LocalDate.now(), LocalDate.now().plusDays(1));

		assertThrows(ResponseStatusException.class, () -> service.create(req, actor));
		verifyNoInteractions(ferienvertreter);
	}

	@Test
	void createRejectsUntilBeforeFrom() {
		UserEntity actor = user();
		when(amt.isAmtHolder(actor)).thenReturn(true);

		var req = new CreateFerienvertreterRequest(UUID.randomUUID(), LocalDate.now(), LocalDate.now().minusDays(1));

		assertThrows(ResponseStatusException.class, () -> service.create(req, actor));
	}

	@Test
	void createSavesEntryWhenActorHoldsAnyAmt() {
		UserEntity actor = user();
		UserEntity person = user();
		when(amt.isAmtHolder(actor)).thenReturn(true);
		when(users.findById(person.getId())).thenReturn(Optional.of(person));

		var from = LocalDate.now();
		var until = from.plusDays(14);
		var req = new CreateFerienvertreterRequest(person.getId(), from, until);

		var result = service.create(req, actor);

		assertEquals(person.getId(), result.person().id());
		assertEquals(from, result.fromDate());
		assertEquals(until, result.untilDate());
		verify(ferienvertreter).save(any(FerienvertreterEntity.class));
	}

	@Test
	void updateRejectsActorWithoutAnyAmt() {
		UserEntity actor = user();
		when(amt.isAmtHolder(actor)).thenReturn(false);

		assertThrows(ResponseStatusException.class,
				() -> service.update(UUID.randomUUID(), new UpdateFerienvertreterRequest(null, null, null), actor));
		verifyNoInteractions(ferienvertreter);
	}

	@Test
	void deleteRejectsActorWithoutAnyAmt() {
		UserEntity actor = user();
		when(amt.isAmtHolder(actor)).thenReturn(false);

		UUID id = UUID.randomUUID();
		assertThrows(ResponseStatusException.class, () -> service.delete(id, actor));
		verify(ferienvertreter, never()).delete(any());
	}

	@Test
	void deleteRemovesEntryWhenActorHoldsAnyAmt() {
		UserEntity actor = user();
		UserEntity person = user();
		when(amt.isAmtHolder(actor)).thenReturn(true);

		var entity = new FerienvertreterEntity(UUID.randomUUID(), person, LocalDate.now(), LocalDate.now().plusDays(1));
		when(ferienvertreter.findById(entity.getId())).thenReturn(Optional.of(entity));

		service.delete(entity.getId(), actor);

		verify(ferienvertreter).delete(entity);
	}

	@Test
	void listReturnsAllOrderedEntries() {
		UserEntity person = user();
		var entity = new FerienvertreterEntity(UUID.randomUUID(), person, LocalDate.now(), LocalDate.now().plusDays(1));
		when(ferienvertreter.findAllOrderedWithUser()).thenReturn(List.of(entity));

		var result = service.list();

		assertEquals(1, result.size());
		assertEquals(person.getId(), result.getFirst().person().id());
	}

	private static UserEntity user() {
		UUID id = UUID.randomUUID();
		UserEntity user = new UserEntity(id, "user-" + id, "User " + id, "hash", false);
		user.setMemberStatus(UserMemberStatus.BURSCH);
		return user;
	}
}
