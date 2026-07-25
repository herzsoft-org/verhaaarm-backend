package moe.herz.verhaarmbackend.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import moe.herz.verhaarmbackend.audit.AuditLogRepository;
import moe.herz.verhaarmbackend.audit.AuditLogService;
import moe.herz.verhaarmbackend.common.ApiValidationException;
import moe.herz.verhaarmbackend.event.dto.ConventBoardChangeDto;
import moe.herz.verhaarmbackend.event.dto.ConventBoardCreateDto;
import moe.herz.verhaarmbackend.event.dto.ConventBoardDto;
import moe.herz.verhaarmbackend.event.dto.ConventBoardItemDto;
import moe.herz.verhaarmbackend.event.dto.UpdateConventBoardRequest;
import moe.herz.verhaarmbackend.periodprotocol.ConventPeriodProtocolRepository;
import moe.herz.verhaarmbackend.user.UserEntity;
import moe.herz.verhaarmbackend.user.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Field;
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

	// EventService.softDeleteAndCleanup (delegated to by the board's delete path) and
	// acquireConventWriteLock (delegated to by the board's write path) both issue native queries via
	// @PersistenceContext EntityManager, unavailable under plain Mockito unit tests - stub the whole
	// chain to a harmless no-op, same pattern as EventServiceTest. Kept as fields (not local to a
	// factory method) so tests can assert call order against them directly.
	private final jakarta.persistence.Query query = mock(jakarta.persistence.Query.class);
	private final jakarta.persistence.EntityManager em = mock(jakarta.persistence.EntityManager.class);
	private final EventService eventService = withMockEntityManager(new EventService(events, audit, protocols));
	private final ConventBoardService service = new ConventBoardService(events, protocols, audit, eventService);

	private EventService withMockEntityManager(EventService service) {
		try {
			lenient().when(query.setParameter(anyString(), any())).thenReturn(query);
			lenient().when(query.getSingleResult()).thenReturn(null);
			lenient().when(query.getResultList()).thenReturn(List.of());
			lenient().when(query.executeUpdate()).thenReturn(0);
			lenient().when(em.createNativeQuery(anyString())).thenReturn(query);

			Field f = EventService.class.getDeclaredField("em");
			f.setAccessible(true);
			f.set(service, em);
			return service;
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
	}

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

	@Test
	void createInsertsANewConventBetweenTwoExistingOnesInTheSameSemester() {
		OffsetDateTime anStart = OffsetDateTime.of(2025, 10, 6, 18, 0, 0, 0, ZoneOffset.UTC);
		EventEntity an = conventEvent(anStart, ConventType.ANCONVENT);
		OffsetDateTime abStart = OffsetDateTime.of(2026, 1, 26, 18, 0, 0, 0, ZoneOffset.UTC);
		EventEntity ab = conventEvent(abStart, ConventType.ABCONVENT);

		when(events.findAllConventsOrderedVisibleForUpdate()).thenReturn(List.of(an, ab));
		when(events.findAllConventsOrderedVisible()).thenReturn(List.of(an, ab));

		OffsetDateTime newStart = OffsetDateTime.of(2025, 12, 1, 18, 0, 0, 0, ZoneOffset.UTC);
		var req = new UpdateConventBoardRequest(
				List.of(),
				List.of(new ConventBoardCreateDto("1. Convent", null, newStart, ConventType.REGULAR, null)),
				List.of()
		);

		assertDoesNotThrow(() -> service.apply(req, admin()));

		ArgumentCaptor<EventEntity> captor = ArgumentCaptor.forClass(EventEntity.class);
		verify(events).save(captor.capture());
		EventEntity created = captor.getValue();
		assertEquals("1. Convent", created.getTitle());
		assertEquals("adH", created.getLocation(), "blank location defaults to adH");
		assertEquals(ConventType.REGULAR, created.getConventType());
		assertTrue(created.isMandatory(), "mandatory defaults to true when omitted");
		assertEquals(EventKind.MAIN, created.getEventKind());
		assertEquals(EventOwnerType.SENIOR, created.getOwnerType());
		assertNotEquals(an.getId(), created.getId());
		assertNotEquals(ab.getId(), created.getId());

		verify(auditRepo, times(1)).save(any());
	}

	@Test
	void createOpensABrandNewSemesterAfterAnExistingAbconvent() {
		OffsetDateTime anStart = OffsetDateTime.of(2025, 10, 6, 18, 0, 0, 0, ZoneOffset.UTC);
		EventEntity an = conventEvent(anStart, ConventType.ANCONVENT);
		OffsetDateTime abStart = OffsetDateTime.of(2026, 1, 26, 18, 0, 0, 0, ZoneOffset.UTC);
		EventEntity ab = conventEvent(abStart, ConventType.ABCONVENT); // closes WS25/26

		when(events.findAllConventsOrderedVisibleForUpdate()).thenReturn(List.of(an, ab));
		when(events.findAllConventsOrderedVisible()).thenReturn(List.of(an, ab));

		OffsetDateTime newAnStart = OffsetDateTime.of(2026, 4, 1, 18, 0, 0, 0, ZoneOffset.UTC);
		var req = new UpdateConventBoardRequest(
				List.of(),
				List.of(new ConventBoardCreateDto("Anconvent", "adH", newAnStart, ConventType.ANCONVENT, true)),
				List.of()
		);

		assertDoesNotThrow(() -> service.apply(req, admin()));

		ArgumentCaptor<EventEntity> captor = ArgumentCaptor.forClass(EventEntity.class);
		verify(events).save(captor.capture());
		assertEquals(ConventType.ANCONVENT, captor.getValue().getConventType());
	}

	@Test
	void deleteRemovesARegularConventThatIsNotAtASemesterBoundary() {
		OffsetDateTime anStart = OffsetDateTime.of(2025, 10, 6, 18, 0, 0, 0, ZoneOffset.UTC);
		EventEntity an = conventEvent(anStart, ConventType.ANCONVENT);
		OffsetDateTime c1Start = OffsetDateTime.of(2025, 11, 3, 18, 0, 0, 0, ZoneOffset.UTC);
		EventEntity c1 = conventEvent(c1Start, ConventType.REGULAR); // to be deleted
		OffsetDateTime abStart = OffsetDateTime.of(2026, 1, 26, 18, 0, 0, 0, ZoneOffset.UTC);
		EventEntity ab = conventEvent(abStart, ConventType.ABCONVENT);

		when(events.findAllConventsOrderedVisibleForUpdate()).thenReturn(List.of(an, c1, ab));
		when(events.findAllConventsOrderedVisible()).thenReturn(List.of(an, ab)); // c1 gone post-delete

		var req = new UpdateConventBoardRequest(List.of(), List.of(), List.of(c1.getId()));

		assertDoesNotThrow(() -> service.apply(req, admin()));

		assertNotNull(c1.getDeletedAt());
		verify(events).save(c1);
		verify(auditRepo, times(1)).save(any());
	}

	@Test
	void deleteAtTheSemesterBoundaryTogetherWithRetypingTheAdjacentConventSucceeds() {
		// The realistic repair: a spurious extra Abconvent was created, and the actual closing
		// Convent needs to be retyped to Abconvent in its place - both must happen together, since
		// retyping c1 alone (while the spurious ab still exists) would leave two Abconvents.
		OffsetDateTime anStart = OffsetDateTime.of(2025, 10, 6, 18, 0, 0, 0, ZoneOffset.UTC);
		EventEntity an = conventEvent(anStart, ConventType.ANCONVENT);
		OffsetDateTime c1Start = OffsetDateTime.of(2025, 11, 3, 18, 0, 0, 0, ZoneOffset.UTC);
		EventEntity c1 = conventEvent(c1Start, ConventType.REGULAR); // to become the real Abconvent
		OffsetDateTime spuriousAbStart = OffsetDateTime.of(2025, 11, 10, 18, 0, 0, 0, ZoneOffset.UTC);
		EventEntity spuriousAb = conventEvent(spuriousAbStart, ConventType.ABCONVENT); // to be deleted

		when(events.findAllConventsOrderedVisibleForUpdate()).thenReturn(List.of(an, c1, spuriousAb));
		when(events.findAllConventsOrderedVisible()).thenReturn(List.of(an, c1));

		var req = new UpdateConventBoardRequest(
				List.of(new ConventBoardChangeDto(c1.getId(), ConventType.ABCONVENT, c1Start)),
				List.of(),
				List.of(spuriousAb.getId())
		);

		assertDoesNotThrow(() -> service.apply(req, admin()));

		assertEquals(ConventType.ABCONVENT, c1.getConventType());
		assertNotNull(spuriousAb.getDeletedAt());
		verify(events).save(c1);
		verify(events).save(spuriousAb);
	}

	@Test
	void deletingAConventWithAnUploadedProtocolIsRejectedWithAClearMessage() {
		OffsetDateTime c1Start = OffsetDateTime.of(2025, 11, 3, 18, 0, 0, 0, ZoneOffset.UTC);
		EventEntity c1 = conventEvent(c1Start, ConventType.REGULAR);

		when(events.findAllConventsOrderedVisibleForUpdate()).thenReturn(List.of(c1));
		when(protocols.findAllPeriodIds()).thenReturn(List.of(c1.getId()));

		var req = new UpdateConventBoardRequest(List.of(), List.of(), List.of(c1.getId()));

		ApiValidationException ex = assertThrows(ApiValidationException.class,
				() -> service.apply(req, admin()));
		assertEquals("CONVENT_HAS_PROTOCOL", ex.getCode());
		assertNull(c1.getDeletedAt());
		verify(events, never()).save(any());
	}

	@Test
	void aBatchWithOneInvalidOperationAppliesNothingAtAll() {
		// A valid create bundled with a delete that's blocked by a Protokoll - the whole batch must
		// be rejected, including the otherwise-fine create.
		OffsetDateTime c1Start = OffsetDateTime.of(2025, 11, 3, 18, 0, 0, 0, ZoneOffset.UTC);
		EventEntity c1 = conventEvent(c1Start, ConventType.REGULAR);

		when(events.findAllConventsOrderedVisibleForUpdate()).thenReturn(List.of(c1));
		when(protocols.findAllPeriodIds()).thenReturn(List.of(c1.getId()));

		OffsetDateTime newStart = OffsetDateTime.of(2025, 12, 1, 18, 0, 0, 0, ZoneOffset.UTC);
		var req = new UpdateConventBoardRequest(
				List.of(),
				List.of(new ConventBoardCreateDto("2. Convent", null, newStart, ConventType.REGULAR, null)),
				List.of(c1.getId())
		);

		assertThrows(ApiValidationException.class, () -> service.apply(req, admin()));

		verify(events, never()).save(any());
		verify(auditRepo, never()).save(any());
	}

	@Test
	void emptyBatchIsRejected() {
		var req = new UpdateConventBoardRequest(List.of(), List.of(), List.of());

		ResponseStatusException ex = assertThrows(ResponseStatusException.class,
				() -> service.validateBatch(req, admin()));
		assertEquals(400, ex.getStatusCode().value());
	}

	@Test
	void applyAcquiresTheConventWriteLockBeforeReadingConventsEvenOnAnEmptyBoard() {
		// Regression for the empty-board race: PESSIMISTIC_WRITE on findAllConventsOrderedVisibleForUpdate
		// locks nothing when there are zero Convent rows, so two concurrent "create the very first
		// Convent" batches could otherwise both validate against the same empty snapshot and both
		// commit. The advisory lock doesn't depend on any row existing - assert it's acquired first.
		when(events.findAllConventsOrderedVisibleForUpdate()).thenReturn(List.of());
		when(events.findAllConventsOrderedVisible()).thenReturn(List.of());

		OffsetDateTime newStart = OffsetDateTime.now(ZoneOffset.UTC).plusDays(30);
		var req = new UpdateConventBoardRequest(
				List.of(),
				List.of(new ConventBoardCreateDto("Anconvent", null, newStart, ConventType.ANCONVENT, null)),
				List.of()
		);

		assertDoesNotThrow(() -> service.apply(req, admin()));

		var order = inOrder(em, events);
		order.verify(em).createNativeQuery(contains("pg_advisory_xact_lock"));
		order.verify(events).findAllConventsOrderedVisibleForUpdate();
	}

	@Test
	void validateBatchNeverAcquiresTheWriteLockSinceItMustStayNonBlocking() {
		when(events.findAllConventsOrderedVisible()).thenReturn(List.of());

		var req = new UpdateConventBoardRequest(
				List.of(),
				List.of(new ConventBoardCreateDto("Anconvent", null, OffsetDateTime.now(ZoneOffset.UTC).plusDays(30), ConventType.ANCONVENT, null)),
				List.of()
		);

		assertDoesNotThrow(() -> service.validateBatch(req, admin()));

		verify(em, never()).createNativeQuery(contains("pg_advisory_xact_lock"));
	}
}
