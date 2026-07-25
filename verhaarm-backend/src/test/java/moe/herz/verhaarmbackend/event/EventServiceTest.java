package moe.herz.verhaarmbackend.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import moe.herz.verhaarmbackend.audit.AuditLogRepository;
import moe.herz.verhaarmbackend.audit.AuditLogService;
import moe.herz.verhaarmbackend.common.ApiValidationException;
import moe.herz.verhaarmbackend.event.dto.CreateEventRequest;
import moe.herz.verhaarmbackend.event.dto.UpdateEventRequest;
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
class EventServiceTest {

	private final EventRepository events = mock(EventRepository.class);
	private final AuditLogRepository auditRepo = mock(AuditLogRepository.class);
	private final AuditLogService audit = new AuditLogService(auditRepo, new ObjectMapper());
	private final ConventPeriodProtocolRepository protocols = mock(ConventPeriodProtocolRepository.class);
	private final EventService service = withMockEntityManager(new EventService(events, audit, protocols));

	private static EventService withMockEntityManager(EventService service) {
		try {
			Field f = EventService.class.getDeclaredField("em");
			f.setAccessible(true);
			f.set(service, mock(EntityManager.class));
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
	void creatingAnconventOnEmptyTimelineSucceeds() {
		when(events.findAllConventsOrderedVisible()).thenReturn(List.of());
		OffsetDateTime startsAt = OffsetDateTime.now(ZoneOffset.UTC).plusDays(30);

		var req = new CreateEventRequest("Anconvent", null, startsAt, true, EventKind.MAIN, ConventType.ANCONVENT);
		var actor = admin();

		when(events.findVisibleById(any())).thenAnswer(inv -> {
			EventEntity e = conventEvent(startsAt, ConventType.ANCONVENT);
			return Optional.of(e);
		});

		var dto = service.create(req, actor);

		assertEquals(ConventType.ANCONVENT, dto.conventType());
		verify(events).save(any());
	}

	@Test
	void creatingEventWithBlankLocationDefaultsToAdH() {
		OffsetDateTime startsAt = OffsetDateTime.now(ZoneOffset.UTC).plusDays(30);
		var req = new CreateEventRequest("Kneipe", "   ", startsAt, false, EventKind.MAIN, null);

		when(events.findVisibleById(any())).thenAnswer(inv -> Optional.of(
				new EventEntity(UUID.randomUUID(), UUID.randomUUID(), "Kneipe", "adH", startsAt, false, EventKind.MAIN, EventOwnerType.SENIOR)
		));

		service.create(req, admin());

		ArgumentCaptor<EventEntity> captor = ArgumentCaptor.forClass(EventEntity.class);
		verify(events).save(captor.capture());
		assertEquals("adH", captor.getValue().getLocation());
	}

	@Test
	void creatingEventWithExplicitLocationKeepsIt() {
		OffsetDateTime startsAt = OffsetDateTime.now(ZoneOffset.UTC).plusDays(30);
		var req = new CreateEventRequest("Kneipe", "  Vereinsheim  ", startsAt, false, EventKind.MAIN, null);

		when(events.findVisibleById(any())).thenAnswer(inv -> Optional.of(
				new EventEntity(UUID.randomUUID(), UUID.randomUUID(), "Kneipe", "Vereinsheim", startsAt, false, EventKind.MAIN, EventOwnerType.SENIOR)
		));

		service.create(req, admin());

		ArgumentCaptor<EventEntity> captor = ArgumentCaptor.forClass(EventEntity.class);
		verify(events).save(captor.capture());
		assertEquals("Vereinsheim", captor.getValue().getLocation());
	}

	@Test
	void updatingEventWithBlankLocationResetsToAdH() {
		OffsetDateTime startsAt = OffsetDateTime.now(ZoneOffset.UTC).plusDays(10);
		EventEntity existing = new EventEntity(
				UUID.randomUUID(), UUID.randomUUID(), "Kneipe", "Vereinsheim", startsAt, false, EventKind.MAIN, EventOwnerType.SENIOR);

		when(events.findVisibleById(existing.getId())).thenReturn(Optional.of(existing));

		var req = new UpdateEventRequest(null, "   ", null, null, null, null, null);

		service.update(existing.getId(), req, admin());

		assertEquals("adH", existing.getLocation());
		verify(events).save(existing);
	}

	@Test
	void updatingEventWithoutTouchingLocationLeavesItUnchanged() {
		OffsetDateTime startsAt = OffsetDateTime.now(ZoneOffset.UTC).plusDays(10);
		EventEntity existing = new EventEntity(
				UUID.randomUUID(), UUID.randomUUID(), "Kneipe", "Vereinsheim", startsAt, false, EventKind.MAIN, EventOwnerType.SENIOR);

		when(events.findVisibleById(existing.getId())).thenReturn(Optional.of(existing));

		var req = new UpdateEventRequest("Kneipe (neu)", null, null, null, null, null, null);

		service.update(existing.getId(), req, admin());

		assertEquals("Vereinsheim", existing.getLocation());
	}

	@Test
	void creatingRegularConventBeforeAnyAnconventIsRejected() {
		when(events.findAllConventsOrderedVisible()).thenReturn(List.of());
		OffsetDateTime startsAt = OffsetDateTime.now(ZoneOffset.UTC).plusDays(30);

		var req = new CreateEventRequest("Convent", null, startsAt, true, EventKind.MAIN, ConventType.REGULAR);

		ApiValidationException ex = assertThrows(ApiValidationException.class, () -> service.create(req, admin()));
		assertEquals(400, ex.getStatus().value());
		assertEquals("CONVENT_SEQUENCE_INVALID", ex.getCode());
		verify(events, never()).save(any());
	}

	@Test
	void movingConventWithExistingProtocolIsBlocked() {
		OffsetDateTime originalStart = OffsetDateTime.now(ZoneOffset.UTC).plusDays(10);
		EventEntity existing = conventEvent(originalStart, ConventType.ABCONVENT);

		when(events.findVisibleById(existing.getId())).thenReturn(Optional.of(existing));
		when(protocols.existsByPeriodId(existing.getId())).thenReturn(true);

		var req = new UpdateEventRequest(null, null, originalStart.plusDays(1), null, null, null, null);

		ApiValidationException ex = assertThrows(ApiValidationException.class,
				() -> service.update(existing.getId(), req, admin()));
		assertEquals(400, ex.getStatus().value());
		assertEquals("CONVENT_HAS_PROTOCOL", ex.getCode());
		verify(events, never()).save(any());
	}

	@Test
	void unmarkingConventWithExistingProtocolIsBlocked() {
		OffsetDateTime startsAt = OffsetDateTime.now(ZoneOffset.UTC).plusDays(10);
		EventEntity existing = conventEvent(startsAt, ConventType.ABCONVENT);

		when(events.findVisibleById(existing.getId())).thenReturn(Optional.of(existing));
		when(protocols.existsByPeriodId(existing.getId())).thenReturn(true);

		var req = new UpdateEventRequest(null, null, null, null, null, null, true);

		ApiValidationException ex = assertThrows(ApiValidationException.class,
				() -> service.update(existing.getId(), req, admin()));
		assertEquals(400, ex.getStatus().value());
		assertEquals("CONVENT_HAS_PROTOCOL", ex.getCode());
	}

	@Test
	void deletingConventWithExistingProtocolIsBlocked() {
		OffsetDateTime startsAt = OffsetDateTime.now(ZoneOffset.UTC).plusDays(10);
		EventEntity existing = conventEvent(startsAt, ConventType.ABCONVENT);

		when(events.findVisibleById(existing.getId())).thenReturn(Optional.of(existing));
		when(protocols.existsByPeriodId(existing.getId())).thenReturn(true);

		ApiValidationException ex = assertThrows(ApiValidationException.class,
				() -> service.delete(existing.getId(), admin()));
		assertEquals(400, ex.getStatus().value());
		assertEquals("CONVENT_HAS_PROTOCOL", ex.getCode());
		verify(events, never()).save(any());
	}

	@Test
	void movingConventWithoutProtocolIsAllowedWhenSequenceStaysValid() {
		OffsetDateTime anStart = OffsetDateTime.now(ZoneOffset.UTC).plusDays(5);
		EventEntity an = conventEvent(anStart, ConventType.ANCONVENT);

		OffsetDateTime abStart = OffsetDateTime.now(ZoneOffset.UTC).plusDays(40);
		EventEntity ab = conventEvent(abStart, ConventType.ABCONVENT);

		when(events.findVisibleById(ab.getId())).thenReturn(Optional.of(ab));
		when(protocols.existsByPeriodId(ab.getId())).thenReturn(false);
		when(events.findAllConventsOrderedVisible()).thenReturn(List.of(an, ab));

		OffsetDateTime newAbStart = abStart.plusDays(1);
		var req = new UpdateEventRequest(null, null, newAbStart, null, null, null, null);

		assertDoesNotThrow(() -> service.update(ab.getId(), req, admin()));
		verify(events).save(ab);
	}

	@Test
	void editingOnlyTheTitleOfAnInconsistentMigratedConventSucceeds() {
		// An orphan Abconvent (no preceding Anconvent) is permanently consistent=false. A pure
		// title edit is non-structural and must not get stuck behind that pre-existing problem.
		OffsetDateTime startsAt = OffsetDateTime.now(ZoneOffset.UTC).plusDays(10);
		EventEntity orphan = conventEvent(startsAt, ConventType.ABCONVENT);

		when(events.findVisibleById(orphan.getId())).thenReturn(Optional.of(orphan));
		when(events.findAllConventsOrderedVisible()).thenReturn(List.of(orphan));

		var req = new UpdateEventRequest("Renamed", null, null, null, null, null, null);

		assertDoesNotThrow(() -> service.update(orphan.getId(), req, admin()));
		verify(events).save(orphan);
	}

	@Test
	void movingConventTimeWithinTheSameBerlinDateIsNotBlockedByAnExistingProtocol() {
		OffsetDateTime originalStart = OffsetDateTime.of(2027, 5, 10, 10, 0, 0, 0, ZoneOffset.UTC);
		OffsetDateTime sameBerlinDateNewTime = OffsetDateTime.of(2027, 5, 10, 12, 0, 0, 0, ZoneOffset.UTC);
		EventEntity existing = conventEvent(originalStart, ConventType.ABCONVENT);

		when(events.findVisibleById(existing.getId())).thenReturn(Optional.of(existing));
		when(events.findAllConventsOrderedVisible()).thenReturn(List.of(existing));
		// lenient: a same-Berlin-date time move must short-circuit before this is even queried
		// (it isn't a structural change) - stubbed true anyway so a regression that removes that
		// short-circuit would actually fail this test instead of silently passing on a false default.
		lenient().when(protocols.existsByPeriodId(existing.getId())).thenReturn(true);

		var req = new UpdateEventRequest(null, null, sameBerlinDateNewTime, null, null, null, null);

		assertDoesNotThrow(() -> service.update(existing.getId(), req, admin()));
		verify(events).save(existing);
	}

	@Test
	void aLeftoverInconsistentLegacyConventNeverBlocksAnUnrelatedCleanCreate() {
		// A migrated singleton semester with no matching Anconvent - permanently inconsistent,
		// but must never deadlock all future Convent management.
		EventEntity legacyOrphan = conventEvent(OffsetDateTime.now(ZoneOffset.UTC).minusDays(2000), ConventType.ABCONVENT);
		when(events.findAllConventsOrderedVisible()).thenReturn(List.of(legacyOrphan));

		OffsetDateTime newAnStart = OffsetDateTime.now(ZoneOffset.UTC).plusDays(30);
		var req = new CreateEventRequest("Anconvent", null, newAnStart, true, EventKind.MAIN, ConventType.ANCONVENT);

		when(events.findVisibleById(any())).thenReturn(Optional.of(conventEvent(newAnStart, ConventType.ANCONVENT)));

		assertDoesNotThrow(() -> service.create(req, admin()));
		verify(events).save(any());
	}

	@Test
	void insertingAConventThatShiftsAnAlreadyProtocolledNeighborIsRejected() {
		OffsetDateTime anStart = OffsetDateTime.now(ZoneOffset.UTC).plusDays(5);
		EventEntity an = conventEvent(anStart, ConventType.ANCONVENT);

		OffsetDateTime c1Start = OffsetDateTime.now(ZoneOffset.UTC).plusDays(40);
		EventEntity c1 = conventEvent(c1Start, ConventType.REGULAR);

		when(events.findAllConventsOrderedVisible()).thenReturn(List.of(an, c1));
		when(protocols.findAllPeriodIds()).thenReturn(List.of(c1.getId()));

		// A new Convent dated between an and c1 shifts c1's derived start date, even though
		// c1 itself is untouched.
		OffsetDateTime c0Start = OffsetDateTime.now(ZoneOffset.UTC).plusDays(20);
		var req = new CreateEventRequest("Convent", null, c0Start, true, EventKind.MAIN, ConventType.REGULAR);

		ApiValidationException ex = assertThrows(ApiValidationException.class, () -> service.create(req, admin()));
		assertEquals(400, ex.getStatus().value());
		assertEquals("CONVENT_PROTOCOL_RANGE_WOULD_CHANGE", ex.getCode());
		verify(events, never()).save(any());
	}

	@Test
	void deletingAnconventThatWouldOrphanLaterConventsIsRejected() {
		OffsetDateTime anStart = OffsetDateTime.now(ZoneOffset.UTC).plusDays(5);
		EventEntity an = conventEvent(anStart, ConventType.ANCONVENT);

		OffsetDateTime abStart = OffsetDateTime.now(ZoneOffset.UTC).plusDays(40);
		EventEntity ab = conventEvent(abStart, ConventType.ABCONVENT);

		when(events.findVisibleById(an.getId())).thenReturn(Optional.of(an));
		when(protocols.existsByPeriodId(an.getId())).thenReturn(false);
		when(events.findAllConventsOrderedVisible()).thenReturn(List.of(an, ab));

		ApiValidationException ex = assertThrows(ApiValidationException.class,
				() -> service.delete(an.getId(), admin()));
		assertEquals(400, ex.getStatus().value());
		assertEquals("CONVENT_SEQUENCE_INVALID", ex.getCode());
		verify(events, never()).save(any());
	}

	@Test
	void housekeepingCannotCreateAnEventMarkedAsConvent() {
		when(events.findAllConventsOrderedVisible()).thenReturn(List.of());
		OffsetDateTime startsAt = OffsetDateTime.now(ZoneOffset.UTC).plusDays(30);

		var req = new CreateEventRequest("Anconvent", null, startsAt, true, EventKind.MAIN, ConventType.ANCONVENT);

		ResponseStatusException ex = assertThrows(ResponseStatusException.class,
				() -> service.create(req, housekeeping()));
		assertEquals(403, ex.getStatusCode().value());
		verify(events, never()).save(any());
	}

	@Test
	void housekeepingCannotMarkTheirOwnEventAsConvent() {
		UserEntity hk = housekeeping();
		OffsetDateTime startsAt = OffsetDateTime.now(ZoneOffset.UTC).plusDays(10);
		EventEntity own = new EventEntity(UUID.randomUUID(), hk.getId(), "Putzdienst", "adH", startsAt, false, EventKind.SECONDARY, EventOwnerType.HOUSEKEEPING);

		when(events.findVisibleById(own.getId())).thenReturn(Optional.of(own));

		var req = new UpdateEventRequest(null, null, null, null, null, ConventType.ANCONVENT, null);

		ResponseStatusException ex = assertThrows(ResponseStatusException.class,
				() -> service.update(own.getId(), req, hk));
		assertEquals(403, ex.getStatusCode().value());
		verify(events, never()).save(any());
	}

	@Test
	void housekeepingCannotMoveAConventTaggedEventTheyOwnWithoutChangingItsType() {
		// Reproduces a legacy-migration edge case: V49 can tag an existing HOUSEKEEPING-owned event
		// as a Convent. Its original HOUSEKEEPING creator must not be able to move/edit/delete it
		// just because they still own it and aren't changing conventType.
		UserEntity hk = housekeeping();
		OffsetDateTime startsAt = OffsetDateTime.now(ZoneOffset.UTC).plusDays(10);
		EventEntity taggedByMigration = new EventEntity(
				UUID.randomUUID(), hk.getId(), "Convent", "adH", startsAt, true, EventKind.MAIN, EventOwnerType.HOUSEKEEPING);
		taggedByMigration.setConventType(ConventType.REGULAR);

		when(events.findVisibleById(taggedByMigration.getId())).thenReturn(Optional.of(taggedByMigration));

		var moveReq = new UpdateEventRequest(null, null, startsAt.plusDays(1), null, null, null, null);
		ResponseStatusException moveEx = assertThrows(ResponseStatusException.class,
				() -> service.update(taggedByMigration.getId(), moveReq, hk));
		assertEquals(403, moveEx.getStatusCode().value());

		ResponseStatusException deleteEx = assertThrows(ResponseStatusException.class,
				() -> service.delete(taggedByMigration.getId(), hk));
		assertEquals(403, deleteEx.getStatusCode().value());

		verify(events, never()).save(any());
	}
}
