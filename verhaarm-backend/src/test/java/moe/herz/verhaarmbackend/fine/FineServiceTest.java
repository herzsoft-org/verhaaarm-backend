package moe.herz.verhaarmbackend.fine;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import moe.herz.verhaarmbackend.audit.AuditLogRepository;
import moe.herz.verhaarmbackend.audit.AuditLogService;
import moe.herz.verhaarmbackend.fine.dto.CreateFineRequest;
import moe.herz.verhaarmbackend.finecatalog.FineCatalogRepository;
import moe.herz.verhaarmbackend.finephoto.FinePhotoService;
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

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FineServiceTest {

	@Mock
	private FineRepository fines;
	@Mock
	private FineCatalogRepository catalog;
	@Mock
	private AuditLogRepository auditRepo;
	@Mock
	private FinePhotoService finePhotos;
	@Mock
	private NotificationService notifications;
	@Mock
	private EntityManager em;

	private FineService service;

	@BeforeEach
	void setUp() {
		service = new FineService(
				fines,
				catalog,
				new AuditLogService(auditRepo, new ObjectMapper()),
				finePhotos,
				notifications
		);
		ReflectionTestUtils.setField(service, "em", em);
	}

	@Test
	void createWithDefaultRequestNotifiesTargetUsers() {
		UserEntity actor = user(UserRole.ADMIN);
		UUID targetId = UUID.randomUUID();
		AtomicReference<FineEntity> saved = stubCreateReload();

		service.create(request(Set.of(targetId), null), actor);

		verify(notifications).createForUser(
				eq(targetId),
				eq(NotificationType.FINE_CREATED),
				eq("Neue Beihängung"),
				eq("Testgrund – 12,34 €"),
				anyMap()
		);
		assertEquals(Set.of(targetId), saved.get().getTargetUserIds());
	}

	@Test
	void createWithNotifyOnlyMeFalseNotifiesTargetUsers() {
		UserEntity actor = user(UserRole.ADMIN);
		UUID targetId = UUID.randomUUID();
		stubCreateReload();

		service.create(request(Set.of(targetId), false), actor);

		verify(notifications).createForUser(eq(targetId), eq(NotificationType.FINE_CREATED), any(), any(), anyMap());
		verify(notifications, never()).createForUser(eq(actor.getId()), any(), any(), any(), anyMap());
	}

	@Test
	void adminCanCreateFineAndNotifyOnlySelfWithoutChangingTargetsOrPayload() {
		UserEntity actor = user(UserRole.ADMIN);
		UUID targetId = UUID.randomUUID();
		AtomicReference<FineEntity> saved = stubCreateReload();

		service.create(request(Set.of(targetId), true), actor);

		@SuppressWarnings("unchecked")
		ArgumentCaptor<Map<String, Object>> dataCaptor = ArgumentCaptor.forClass(Map.class);
		verify(notifications).createForUser(
				eq(actor.getId()),
				eq(NotificationType.FINE_CREATED),
				eq("Neue Beihängung"),
				eq("Testgrund – 12,34 €"),
				dataCaptor.capture()
		);
		verify(notifications, never()).createForUser(eq(targetId), any(), any(), any(), anyMap());
		assertEquals(Set.of(targetId), saved.get().getTargetUserIds());
		assertEquals(saved.get().getId().toString(), dataCaptor.getValue().get("fineId"));
		assertEquals(1234, dataCaptor.getValue().get("amountCents"));
	}

	@Test
	void nonAdminCannotUseFineNotifyOnlyMe() {
		UserEntity actor = user(UserRole.SENIOR);

		ResponseStatusException ex = assertThrows(
				ResponseStatusException.class,
				() -> service.create(request(Set.of(UUID.randomUUID()), true), actor)
		);

		assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
		assertEquals("notifyOnlyMe requires ADMIN", ex.getReason());
		verify(fines, never()).save(any());
		verifyNoInteractions(notifications);
	}

	private AtomicReference<FineEntity> stubCreateReload() {
		AtomicReference<FineEntity> saved = new AtomicReference<>();
		when(fines.save(any(FineEntity.class))).thenAnswer(invocation -> {
			FineEntity fine = invocation.getArgument(0);
			saved.set(fine);
			return fine;
		});
		when(fines.findVisibleById(any(UUID.class))).thenAnswer(invocation -> Optional.of(saved.get()));
		return saved;
	}

	private static CreateFineRequest request(Set<UUID> targetUserIds, Boolean notifyOnlyMe) {
		return new CreateFineRequest(
				LocalDate.of(2026, 5, 28),
				null,
				"Testgrund",
				1234,
				targetUserIds,
				notifyOnlyMe
		);
	}

	private static UserEntity user(UserRole... roles) {
		UUID id = UUID.randomUUID();
		UserEntity user = new UserEntity(id, "user-" + id, "User " + id, "hash", false);
		for (UserRole role : roles) user.addRole(role);
		return user;
	}
}
