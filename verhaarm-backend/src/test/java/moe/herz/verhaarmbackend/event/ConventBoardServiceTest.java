package moe.herz.verhaarmbackend.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import moe.herz.verhaarmbackend.audit.AuditLogRepository;
import moe.herz.verhaarmbackend.audit.AuditLogService;
import moe.herz.verhaarmbackend.common.ApiValidationException;
import moe.herz.verhaarmbackend.event.dto.ConventBoardChangeDto;
import moe.herz.verhaarmbackend.event.dto.ConventBoardDto;
import moe.herz.verhaarmbackend.event.dto.ConventBoardItemDto;
import moe.herz.verhaarmbackend.event.dto.UpdateConventBoardRequest;
import moe.herz.verhaarmbackend.periodprotocol.ConventPeriodProtocolRepository;
import moe.herz.verhaarmbackend.user.UserEntity;
import moe.herz.verhaarmbackend.user.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConventBoardServiceTest {

	private final EventRepository events = mock(EventRepository.class);
	private final ConventPeriodProtocolRepository protocols = mock(ConventPeriodProtocolRepository.class);
	private final AuditLogRepository auditRepo = mock(AuditLogRepository.class);
	private final AuditLogService audit = new AuditLogService(auditRepo, new ObjectMapper());
	private final ConventBoardService service = new ConventBoardService(events, protocols, audit);

	private static UserEntity admin() {
		UserEntity u = new UserEntity(UUID.randomUUID(), "admin", "Admin", "hash", false);
		u.addRole(UserRole.ADMIN);
		return u;
	}

	private static UserEntity housekeeping() {
		UserEntity u = new UserEntity(UUID.randomUUID(), "housekeeping", "Housekeeping", "hash", false);
		u.addRole(UserRole.HOUSEKEEPING);
		return u;
	}

	private static EventEntity conventEvent(OffsetDateTime startsAt, ConventType type) {
		EventEntity e = new EventEntity(UUID.randomUUID(), UUID.randomUUID(), "Convent", "adH", startsAt, true, EventKind.MAIN, EventOwnerType.SENIOR);
		e.setConventType(type);
		return e;
	}

	@Test
	void nonAdminSeniorCannotReadOrWriteTheBoard() {
		UserEntity hk = housekeeping();

		assertThrows(ResponseStatusException.class, () -> service.board(hk));
		assertThrows(ResponseStatusException.class, () -> service.apply(
				new UpdateConventBoardRequest(List.of(new ConventBoardChangeDto(UUID.randomUUID(), ConventType.REGULAR, OffsetDateTime.now(ZoneOffset.UTC)))),
				hk
		));
	}

	@Test
	void boardGroupsConventsIntoSemesterBlocksInChronologicalOrder() {
		EventEntity an = conventEvent(OffsetDateTime.of(2025, 10, 6, 18, 0, 0, 0, ZoneOffset.UTC), ConventType.ANCONVENT);
		EventEntity c1 = conventEvent(OffsetDateTime.of(2025, 11, 3, 18, 0, 0, 0, ZoneOffset.UTC), ConventType.REGULAR);
		EventEntity ab = conventEvent(OffsetDateTime.of(2026, 1, 26, 18, 0, 0, 0, ZoneOffset.UTC), ConventType.ABCONVENT);
		when(events.findAllConventsOrderedVisible()).thenReturn(List.of(an, c1, ab));

		ConventBoardDto board = service.board(admin());

		assertEquals(1, board.semesters().size());
		assertEquals("WS25/26", board.semesters().getFirst().semester());
		List<ConventBoardItemDto> items = board.semesters().getFirst().convents();
		assertEquals(3, items.size());
		assertEquals("Anconvent", items.get(0).label());
		assertEquals("1. Convent", items.get(1).label());
		assertEquals("Abconvent", items.get(2).label());
		assertTrue(items.stream().allMatch(ConventBoardItemDto::consistent));
	}

	@Test
	void reproducesTheReportedDeadlock_swappedAnconventAbconventTypesFixedTogether() {
		// The real bug: a WS Anconvent was migrated as the type ABCONVENT, and its Abconvent as
		// ANCONVENT (types swapped). Retyping only one of them alone would leave the other one
		// freshly inconsistent - a coordinated batch is required to reach the valid final state.
		OffsetDateTime aStart = OffsetDateTime.of(2025, 10, 6, 18, 0, 0, 0, ZoneOffset.UTC);
		OffsetDateTime bStart = OffsetDateTime.of(2026, 1, 26, 18, 0, 0, 0, ZoneOffset.UTC);
		EventEntity a = conventEvent(aStart, ConventType.ABCONVENT); // should be ANCONVENT
		EventEntity b = conventEvent(bStart, ConventType.ANCONVENT); // should be ABCONVENT

		when(events.findAllConventsOrderedVisibleForUpdate()).thenReturn(List.of(a, b));
		when(events.findAllConventsOrderedVisible()).thenReturn(List.of(a, b));

		var req = new UpdateConventBoardRequest(List.of(
				new ConventBoardChangeDto(a.getId(), ConventType.ANCONVENT, aStart),
				new ConventBoardChangeDto(b.getId(), ConventType.ABCONVENT, bStart)
		));

		ConventBoardDto result = assertDoesNotThrow(() -> service.apply(req, admin()));

		assertEquals(ConventType.ANCONVENT, a.getConventType());
		assertEquals(ConventType.ABCONVENT, b.getConventType());
		verify(events).save(a);
		verify(events).save(b);

		List<ConventBoardItemDto> items = result.semesters().getFirst().convents();
		assertTrue(items.stream().allMatch(ConventBoardItemDto::consistent), "both Convente are consistent after the coordinated fix");
	}

	@Test
	void batchRejectsWhenOnlyOneOfTheSwappedTypesIsCorrected() {
		OffsetDateTime aStart = OffsetDateTime.of(2025, 10, 6, 18, 0, 0, 0, ZoneOffset.UTC);
		OffsetDateTime bStart = OffsetDateTime.of(2026, 1, 26, 18, 0, 0, 0, ZoneOffset.UTC);
		EventEntity a = conventEvent(aStart, ConventType.ABCONVENT);
		EventEntity b = conventEvent(bStart, ConventType.ANCONVENT);

		when(events.findAllConventsOrderedVisible()).thenReturn(List.of(a, b));

		// Only fixing `a` leaves two Anconvents back-to-back (b was consistent before, now isn't).
		var partialReq = new UpdateConventBoardRequest(List.of(
				new ConventBoardChangeDto(a.getId(), ConventType.ANCONVENT, aStart)
		));

		ApiValidationException ex = assertThrows(ApiValidationException.class,
				() -> service.validateBatch(partialReq, admin()));
		assertEquals("CONVENT_SEQUENCE_INVALID", ex.getCode());
		verify(events, never()).save(any());
	}

	@Test
	void batchIsRejectedWhenItWouldShiftAProtocolledConventsRange() {
		OffsetDateTime anStart = OffsetDateTime.of(2025, 10, 6, 18, 0, 0, 0, ZoneOffset.UTC);
		EventEntity an = conventEvent(anStart, ConventType.ANCONVENT);
		OffsetDateTime c1Start = OffsetDateTime.of(2025, 11, 20, 18, 0, 0, 0, ZoneOffset.UTC);
		EventEntity c1 = conventEvent(c1Start, ConventType.REGULAR); // has a protocol

		when(events.findAllConventsOrderedVisibleForUpdate()).thenReturn(List.of(an, c1));
		when(protocols.findAllPeriodIds()).thenReturn(List.of(c1.getId()));

		// Moving `an` later shifts c1's derived start date even though c1 itself isn't in the batch.
		var req = new UpdateConventBoardRequest(List.of(
				new ConventBoardChangeDto(an.getId(), ConventType.ANCONVENT, anStart.plusDays(10))
		));

		ApiValidationException ex = assertThrows(ApiValidationException.class,
				() -> service.apply(req, admin()));
		assertEquals("CONVENT_PROTOCOL_RANGE_WOULD_CHANGE", ex.getCode());
		verify(events, never()).save(any());
	}

	@Test
	void resubmittingAnUnrelatedInconsistentLegacyConventUnchangedDoesNotBlockTheBatch() {
		EventEntity legacyOrphan = conventEvent(OffsetDateTime.of(2020, 3, 1, 18, 0, 0, 0, ZoneOffset.UTC), ConventType.ABCONVENT);
		EventEntity newAn = conventEvent(OffsetDateTime.of(2025, 10, 6, 18, 0, 0, 0, ZoneOffset.UTC), ConventType.REGULAR); // to be retyped

		when(events.findAllConventsOrderedVisible()).thenReturn(List.of(legacyOrphan, newAn));

		var req = new UpdateConventBoardRequest(List.of(
				// legacyOrphan included verbatim (e.g. the client always submits the whole board) -
				// unchanged, so it must not be required to suddenly become consistent.
				new ConventBoardChangeDto(legacyOrphan.getId(), legacyOrphan.getConventType(), legacyOrphan.getStartsAt()),
				new ConventBoardChangeDto(newAn.getId(), ConventType.ANCONVENT, newAn.getStartsAt())
		));

		assertDoesNotThrow(() -> service.validateBatch(req, admin()));
	}

	@Test
	void applyingABatchThatResubmitsEveryConventSkipsSaveAndAuditForTrueNoOps() {
		OffsetDateTime anStart = OffsetDateTime.of(2025, 10, 6, 18, 0, 0, 0, ZoneOffset.UTC);
		EventEntity an = conventEvent(anStart, ConventType.ANCONVENT); // untouched
		OffsetDateTime abStart = OffsetDateTime.of(2026, 1, 26, 18, 0, 0, 0, ZoneOffset.UTC);
		EventEntity ab = conventEvent(abStart, ConventType.REGULAR); // to become the Abconvent

		when(events.findAllConventsOrderedVisibleForUpdate()).thenReturn(List.of(an, ab));
		when(events.findAllConventsOrderedVisible()).thenReturn(List.of(an, ab));

		// The board always resubmits every Convent - `an` comes back byte-identical.
		var req = new UpdateConventBoardRequest(List.of(
				new ConventBoardChangeDto(an.getId(), ConventType.ANCONVENT, anStart),
				new ConventBoardChangeDto(ab.getId(), ConventType.ABCONVENT, abStart)
		));

		assertDoesNotThrow(() -> service.apply(req, admin()));

		verify(events, never()).save(an);
		verify(events).save(ab);
		verify(auditRepo, times(1)).save(any()); // exactly one entry - for `ab` only, not the untouched `an`
	}

	@Test
	void applyUsesThePessimisticWriteQueryWhileReadAndDryRunDoNot() {
		OffsetDateTime anStart = OffsetDateTime.of(2025, 10, 6, 18, 0, 0, 0, ZoneOffset.UTC);
		EventEntity an = conventEvent(anStart, ConventType.ANCONVENT);

		when(events.findAllConventsOrderedVisible()).thenReturn(List.of(an));
		assertDoesNotThrow(() -> service.board(admin()));
		assertDoesNotThrow(() -> service.validateBatch(
				new UpdateConventBoardRequest(List.of(new ConventBoardChangeDto(an.getId(), ConventType.ANCONVENT, anStart))),
				admin()
		));
		verify(events, never()).findAllConventsOrderedVisibleForUpdate();

		when(events.findAllConventsOrderedVisibleForUpdate()).thenReturn(List.of(an));
		assertDoesNotThrow(() -> service.apply(
				new UpdateConventBoardRequest(List.of(new ConventBoardChangeDto(an.getId(), ConventType.ANCONVENT, anStart))),
				admin()
		));
		verify(events, atLeastOnce()).findAllConventsOrderedVisibleForUpdate();
	}

	@Test
	void duplicateEventIdInOneBatchIsRejected() {
		EventEntity a = conventEvent(OffsetDateTime.of(2025, 10, 6, 18, 0, 0, 0, ZoneOffset.UTC), ConventType.ANCONVENT);
		when(events.findAllConventsOrderedVisible()).thenReturn(List.of(a));

		var req = new UpdateConventBoardRequest(List.of(
				new ConventBoardChangeDto(a.getId(), ConventType.ANCONVENT, a.getStartsAt()),
				new ConventBoardChangeDto(a.getId(), ConventType.REGULAR, a.getStartsAt())
		));

		ResponseStatusException ex = assertThrows(ResponseStatusException.class,
				() -> service.validateBatch(req, admin()));
		assertEquals(400, ex.getStatusCode().value());
	}

	@Test
	void unknownEventIdInBatchIsRejected() {
		when(events.findAllConventsOrderedVisible()).thenReturn(List.of());

		var req = new UpdateConventBoardRequest(List.of(
				new ConventBoardChangeDto(UUID.randomUUID(), ConventType.ANCONVENT, OffsetDateTime.now(ZoneOffset.UTC))
		));

		ResponseStatusException ex = assertThrows(ResponseStatusException.class,
				() -> service.validateBatch(req, admin()));
		assertEquals(404, ex.getStatusCode().value());
	}

	@Test
	void nonConventEventInBatchIsRejectedWithAClearMessageNotAGenericNotFound() {
		EventEntity notAConvent = new EventEntity(
				UUID.randomUUID(), UUID.randomUUID(), "Kneipe", "adH",
				OffsetDateTime.now(ZoneOffset.UTC).plusDays(5), false, EventKind.SECONDARY, EventOwnerType.SENIOR);
		when(events.findAllConventsOrderedVisible()).thenReturn(List.of());
		when(events.findVisibleById(notAConvent.getId())).thenReturn(Optional.of(notAConvent));

		var req = new UpdateConventBoardRequest(List.of(
				new ConventBoardChangeDto(notAConvent.getId(), ConventType.REGULAR, notAConvent.getStartsAt())
		));

		ResponseStatusException ex = assertThrows(ResponseStatusException.class,
				() -> service.validateBatch(req, admin()));
		assertEquals(400, ex.getStatusCode().value(), "the event exists but isn't a Convent, so this is a 400 not a 404");
	}
}
