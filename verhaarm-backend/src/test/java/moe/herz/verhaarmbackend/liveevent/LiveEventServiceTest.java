package moe.herz.verhaarmbackend.liveevent;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import moe.herz.verhaarmbackend.audit.AuditLogRepository;
import moe.herz.verhaarmbackend.audit.AuditLogService;
import moe.herz.verhaarmbackend.event.EventEntity;
import moe.herz.verhaarmbackend.event.EventKind;
import moe.herz.verhaarmbackend.event.EventOwnerType;
import moe.herz.verhaarmbackend.event.EventRepository;
import moe.herz.verhaarmbackend.liveevent.dto.CreateLiveEventRequest;
import moe.herz.verhaarmbackend.notification.NotificationService;
import moe.herz.verhaarmbackend.notification.NotificationType;
import moe.herz.verhaarmbackend.user.UserEntity;
import moe.herz.verhaarmbackend.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LiveEventServiceTest {

	@Mock
	private LiveEventRepository liveEvents;
	@Mock
	private EventRepository events;
	@Mock
	private LiveEventReactionRepository reactions;
	@Mock
	private AuditLogRepository auditRepo;
	@Mock
	private NotificationService notifications;
	@Mock
	private EntityManager em;

	private LiveEventService service;

	@BeforeEach
	void setUp() {
		service = new LiveEventService(
				liveEvents,
				events,
				reactions,
				new AuditLogService(auditRepo, new ObjectMapper()),
				notifications
		);
		ReflectionTestUtils.setField(service, "em", em);
	}

	@Test
	void authenticatedUserCanToggleProst() {
		UserEntity actor = user(UserRole.MEMBER);
		LiveEventEntity event = liveEvent(actor.getId());
		when(liveEvents.findVisibleById(event.getId())).thenReturn(Optional.of(event));
		when(reactions.findByLiveEventIdAndUserIdAndType(event.getId(), actor.getId(), LiveEventReactionType.PROST))
				.thenReturn(Optional.empty());
		when(reactions.countByLiveEventIdAndType(event.getId(), LiveEventReactionType.PROST)).thenReturn(1L);
		when(reactions.countByLiveEventIdAndType(event.getId(), LiveEventReactionType.ICH_KOMME)).thenReturn(0L);
		when(reactions.existsByLiveEventIdAndUserIdAndType(event.getId(), actor.getId(), LiveEventReactionType.PROST)).thenReturn(true);
		when(reactions.existsByLiveEventIdAndUserIdAndType(event.getId(), actor.getId(), LiveEventReactionType.ICH_KOMME)).thenReturn(false);

		var summary = service.toggleReaction(event.getId(), LiveEventReactionType.PROST, actor);

		assertEquals(1, summary.prostCount());
		assertEquals(0, summary.ichKommeCount());
		assertTrue(summary.reactedProst());
		assertFalse(summary.reactedIchKomme());
		verify(reactions).save(any(LiveEventReactionEntity.class));
		verify(reactions, never()).delete(any());
	}

	@Test
	void authenticatedUserCanToggleIchKomme() {
		UserEntity actor = user(UserRole.MEMBER);
		LiveEventEntity event = liveEvent(actor.getId());
		when(liveEvents.findVisibleById(event.getId())).thenReturn(Optional.of(event));
		when(reactions.findByLiveEventIdAndUserIdAndType(event.getId(), actor.getId(), LiveEventReactionType.ICH_KOMME))
				.thenReturn(Optional.empty());
		when(reactions.countByLiveEventIdAndType(event.getId(), LiveEventReactionType.PROST)).thenReturn(0L);
		when(reactions.countByLiveEventIdAndType(event.getId(), LiveEventReactionType.ICH_KOMME)).thenReturn(1L);
		when(reactions.existsByLiveEventIdAndUserIdAndType(event.getId(), actor.getId(), LiveEventReactionType.PROST)).thenReturn(false);
		when(reactions.existsByLiveEventIdAndUserIdAndType(event.getId(), actor.getId(), LiveEventReactionType.ICH_KOMME)).thenReturn(true);

		var summary = service.toggleReaction(event.getId(), LiveEventReactionType.ICH_KOMME, actor);

		assertEquals(0, summary.prostCount());
		assertEquals(1, summary.ichKommeCount());
		assertFalse(summary.reactedProst());
		assertTrue(summary.reactedIchKomme());
		verify(reactions).save(any(LiveEventReactionEntity.class));
		verify(reactions, never()).delete(any());
	}

	@Test
	void toggleRemovesExistingReactionInsteadOfCreatingDuplicate() {
		UserEntity actor = user(UserRole.MEMBER);
		LiveEventEntity event = liveEvent(actor.getId());
		LiveEventReactionEntity existing = new LiveEventReactionEntity(UUID.randomUUID(), event.getId(), actor.getId(), LiveEventReactionType.PROST);
		when(liveEvents.findVisibleById(event.getId())).thenReturn(Optional.of(event));
		when(reactions.findByLiveEventIdAndUserIdAndType(event.getId(), actor.getId(), LiveEventReactionType.PROST))
				.thenReturn(Optional.of(existing));

		var summary = service.toggleReaction(event.getId(), LiveEventReactionType.PROST, actor);

		assertEquals(0, summary.prostCount());
		assertEquals(0, summary.ichKommeCount());
		assertFalse(summary.reactedProst());
		assertFalse(summary.reactedIchKomme());
		verify(reactions).delete(existing);
		verify(reactions, never()).save(any());
	}

	@Test
	void listIncludesCountsAndCurrentUserReactionFlags() {
		UserEntity actor = user(UserRole.MEMBER);
		LiveEventEntity event = liveEvent(actor.getId());
		when(liveEvents.findActiveVisible(any())).thenReturn(List.of(event));
		when(reactions.countByLiveEventIdAndType(event.getId(), LiveEventReactionType.PROST)).thenReturn(3L);
		when(reactions.countByLiveEventIdAndType(event.getId(), LiveEventReactionType.ICH_KOMME)).thenReturn(2L);
		when(reactions.existsByLiveEventIdAndUserIdAndType(event.getId(), actor.getId(), LiveEventReactionType.PROST)).thenReturn(true);
		when(reactions.existsByLiveEventIdAndUserIdAndType(event.getId(), actor.getId(), LiveEventReactionType.ICH_KOMME)).thenReturn(false);

		var result = service.listActive(actor);

		assertEquals(1, result.size());
		assertEquals(3, result.getFirst().reactions().prostCount());
		assertEquals(2, result.getFirst().reactions().ichKommeCount());
		assertTrue(result.getFirst().reactions().reactedProst());
		assertFalse(result.getFirst().reactions().reactedIchKomme());
		assertNull(result.getFirst().reactionUsers());
		verifyNoInteractions(events);
	}

	@Test
	void scheduledEventMaterializationCreatesLiveEventAndNotificationWithoutAUserRequest() {
		UserEntity creator = user(UserRole.MEMBER);
		OffsetDateTime startsAt = OffsetDateTime.now().minusMinutes(1);
		var scheduledEvent = new EventEntity(
				UUID.randomUUID(),
				creator.getId(),
				"Geplanter Termin",
				startsAt,
				false,
				EventKind.SECONDARY,
				EventOwnerType.SENIOR
		);
		when(events.findRecentlyStartedVisible(any(), any())).thenReturn(List.of(scheduledEvent));
		when(liveEvents.existsBySourceEventId(scheduledEvent.getId())).thenReturn(false);

		int count = service.materializeRecentlyStartedEvents();

		assertEquals(1, count);
		ArgumentCaptor<LiveEventEntity> liveEventCaptor = ArgumentCaptor.forClass(LiveEventEntity.class);
		verify(liveEvents).save(liveEventCaptor.capture());
		LiveEventEntity materialized = liveEventCaptor.getValue();
		assertEquals(scheduledEvent.getId(), materialized.getSourceEventId());
		assertEquals("Geplanter Termin", materialized.getTitle());
		assertEquals(startsAt.plusHours(2), materialized.getExpiresAt());
		verify(notifications).createForEnabledUsersWithPush(
				eq(NotificationType.LIVE_EVENT_CREATED),
				eq("Das geht gerade:"),
				eq("Geplanter Termin"),
				anyMap()
		);
	}

	@Test
	void detailIncludesReactionUserLists() {
		UserEntity actor = user(UserRole.MEMBER);
		UserEntity prostUser = user(UserRole.MEMBER);
		UserEntity ichKommeUser = user(UserRole.MEMBER);
		LiveEventEntity event = liveEvent(actor.getId());
		when(liveEvents.findVisibleById(event.getId())).thenReturn(Optional.of(event));
		when(reactions.findUsersByLiveEventIdAndType(event.getId(), LiveEventReactionType.PROST)).thenReturn(List.of(prostUser));
		when(reactions.findUsersByLiveEventIdAndType(event.getId(), LiveEventReactionType.ICH_KOMME)).thenReturn(List.of(ichKommeUser));

		var result = service.getVisible(event.getId(), actor);

		assertNotNull(result.reactionUsers());
		assertEquals(prostUser.getId(), result.reactionUsers().prost().getFirst().id());
		assertEquals(prostUser.getDisplayName(), result.reactionUsers().prost().getFirst().displayName());
		assertEquals(ichKommeUser.getId(), result.reactionUsers().ichKomme().getFirst().id());
		assertEquals(ichKommeUser.getDisplayName(), result.reactionUsers().ichKomme().getFirst().displayName());
	}

	@Test
	void createNotificationPayloadIncludesReactionActionMetadata() {
		UserEntity actor = user(UserRole.MEMBER);
		AtomicReference<LiveEventEntity> saved = new AtomicReference<>();
		when(liveEvents.save(any(LiveEventEntity.class))).thenAnswer(invocation -> {
			LiveEventEntity event = invocation.getArgument(0);
			saved.set(event);
			return event;
		});
		when(liveEvents.findById(any(UUID.class))).thenAnswer(invocation -> Optional.of(saved.get()));

		service.create(new CreateLiveEventRequest("Titel", "Ort", "Text", null), actor);

		@SuppressWarnings("unchecked")
		ArgumentCaptor<Map<String, Object>> dataCaptor = ArgumentCaptor.forClass(Map.class);
		verify(notifications).createForEnabledUsersWithPush(
				eq(NotificationType.LIVE_EVENT_CREATED),
				eq("Das geht gerade:"),
				eq("Titel"),
				dataCaptor.capture()
		);

		Map<String, Object> data = dataCaptor.getValue();
		assertEquals(saved.get().getId().toString(), data.get("liveEventId"));
		assertEquals("true", data.get("supportsActions"));
		assertEquals("LIVE_EVENT_REACTIONS", data.get("actionSet"));
		assertEquals("/live-events/" + saved.get().getId() + "/reactions/{type}", data.get("reactionEndpoint"));
		assertEquals("PROST,ICH_KOMME", data.get("reactionTypes"));
	}

	@Test
	void createWithNotifyOnlyMeFalseUsesNormalRecipients() {
		UserEntity actor = user(UserRole.ADMIN);
		AtomicReference<LiveEventEntity> saved = stubCreateReload();

		service.create(new CreateLiveEventRequest("Titel", "Ort", "Text", false), actor);

		verify(notifications).createForEnabledUsersWithPush(
				eq(NotificationType.LIVE_EVENT_CREATED),
				eq("Das geht gerade:"),
				eq("Titel"),
				anyMap()
		);
		verify(notifications, never()).createForUser(eq(actor.getId()), any(), any(), any(), anyMap());
		assertNotNull(saved.get());
	}

	@Test
	void adminCanCreateLiveEventAndNotifyOnlySelf() {
		UserEntity actor = user(UserRole.ADMIN);
		AtomicReference<LiveEventEntity> saved = stubCreateReload();

		service.create(new CreateLiveEventRequest("Titel", "Ort", "Text", true), actor);

		@SuppressWarnings("unchecked")
		ArgumentCaptor<Map<String, Object>> dataCaptor = ArgumentCaptor.forClass(Map.class);
		verify(notifications).createForUser(
				eq(actor.getId()),
				eq(NotificationType.LIVE_EVENT_CREATED),
				eq("Das geht gerade:"),
				eq("Titel"),
				dataCaptor.capture()
		);
		verify(notifications, never()).createForEnabledUsersWithPush(any(), any(), any(), anyMap());
		assertEquals(saved.get().getId().toString(), dataCaptor.getValue().get("liveEventId"));
		assertEquals("LIVE_EVENT_REACTIONS", dataCaptor.getValue().get("actionSet"));
	}

	@Test
	void nonAdminCannotUseLiveEventNotifyOnlyMe() {
		UserEntity actor = user(UserRole.MEMBER);

		ResponseStatusException ex = assertThrows(
				ResponseStatusException.class,
				() -> service.create(new CreateLiveEventRequest("Titel", "Ort", "Text", true), actor)
		);

		assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
		assertEquals("notifyOnlyMe requires ADMIN", ex.getReason());
		verify(liveEvents, never()).save(any());
		verifyNoInteractions(notifications);
	}

	private AtomicReference<LiveEventEntity> stubCreateReload() {
		AtomicReference<LiveEventEntity> saved = new AtomicReference<>();
		when(liveEvents.save(any(LiveEventEntity.class))).thenAnswer(invocation -> {
			LiveEventEntity event = invocation.getArgument(0);
			saved.set(event);
			return event;
		});
		when(liveEvents.findById(any(UUID.class))).thenAnswer(invocation -> Optional.of(saved.get()));
		return saved;
	}

	private static LiveEventEntity liveEvent(UUID creatorId) {
		return new LiveEventEntity(
				UUID.randomUUID(),
				"Titel",
				"Ort",
				"Text",
				creatorId,
				null,
				OffsetDateTime.now().plusHours(1)
		);
	}

	private static UserEntity user(UserRole... roles) {
		UUID id = UUID.randomUUID();
		UserEntity user = new UserEntity(id, "user-" + id, "User " + id, "hash", false);
		for (UserRole role : roles) user.addRole(role);
		return user;
	}
}
