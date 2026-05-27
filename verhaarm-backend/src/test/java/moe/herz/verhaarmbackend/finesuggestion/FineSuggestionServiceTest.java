package moe.herz.verhaarmbackend.finesuggestion;

import jakarta.persistence.EntityManager;
import moe.herz.verhaarmbackend.audit.AuditLogService;
import moe.herz.verhaarmbackend.fine.FineRepository;
import moe.herz.verhaarmbackend.fine.FineType;
import moe.herz.verhaarmbackend.finecatalog.FineCatalogRepository;
import moe.herz.verhaarmbackend.finesuggestion.dto.CreateFineSuggestionRequest;
import moe.herz.verhaarmbackend.finesuggestionphoto.FineSuggestionPhotoService;
import moe.herz.verhaarmbackend.notification.NotificationService;
import moe.herz.verhaarmbackend.notification.NotificationType;
import moe.herz.verhaarmbackend.user.UserEntity;
import moe.herz.verhaarmbackend.user.UserRepository;
import moe.herz.verhaarmbackend.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FineSuggestionServiceTest {

	@Mock
	private FineSuggestionRepository suggestions;
	@Mock
	private FineRepository fines;
	@Mock
	private FineCatalogRepository catalog;
	@Mock
	private AuditLogService audit;
	@Mock
	private FineSuggestionPhotoService suggestionPhotos;
	@Mock
	private UserRepository users;
	@Mock
	private NotificationService notifications;
	@Mock
	private EntityManager em;

	private FineSuggestionService service;

	@BeforeEach
	void setUp() {
		service = new FineSuggestionService(
				suggestions,
				fines,
				catalog,
				audit,
				suggestionPhotos,
				users,
				notifications
		);
		ReflectionTestUtils.setField(service, "em", em);
	}

	@Test
	void createNotifiesSeniorAndHousekeepingOnceWithFineSuggestionsTargetData() {
		UserEntity actor = user(UUID.randomUUID(), UserRole.MEMBER);
		UserEntity senior = user(UUID.randomUUID(), UserRole.SENIOR);
		UserEntity housekeeping = user(UUID.randomUUID(), UserRole.HOUSEKEEPING);
		UserEntity bothRoles = user(UUID.randomUUID(), UserRole.SENIOR, UserRole.HOUSEKEEPING);
		UserEntity member = user(UUID.randomUUID(), UserRole.MEMBER);

		when(users.findAllEnabledUsersWithRoles()).thenReturn(List.of(senior, housekeeping, bothRoles, member));
		when(suggestions.save(any(FineSuggestionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(suggestions.findVisibleById(any(UUID.class))).thenAnswer(invocation -> {
			UUID id = invocation.getArgument(0);
			FineSuggestionEntity entity = new FineSuggestionEntity(
					id,
					LocalDate.of(2026, 5, 27),
					actor.getId(),
					null,
					"Grund",
					500,
					FineType.CUSTOM
			);
			entity.addTarget(member.getId());
			return Optional.of(entity);
		});

		service.create(
				new CreateFineSuggestionRequest(
						LocalDate.of(2026, 5, 27),
						null,
						"Grund",
						500,
						Set.of(member.getId())
				),
				actor
		);

		ArgumentCaptor<UUID> userIdCaptor = ArgumentCaptor.forClass(UUID.class);
		@SuppressWarnings("unchecked")
		ArgumentCaptor<Map<String, Object>> dataCaptor = ArgumentCaptor.forClass(Map.class);

		verify(notifications, org.mockito.Mockito.times(3)).createForUser(
				userIdCaptor.capture(),
				eq(NotificationType.FINE_SUGGESTION_CREATED),
				eq("Neue vorgeschlagene Beihängung"),
				eq("Es wurde eine neue Beihängung vorgeschlagen."),
				dataCaptor.capture()
		);

		assertEquals(Set.of(senior.getId(), housekeeping.getId(), bothRoles.getId()), Set.copyOf(userIdCaptor.getAllValues()));
		assertEquals(3, userIdCaptor.getAllValues().size());
		for (Map<String, Object> data : dataCaptor.getAllValues()) {
			assertTrue(data.containsKey("suggestionId"));
			assertEquals("2026-05-27", data.get("fineDate"));
		}
	}

	private static UserEntity user(UUID id, UserRole... roles) {
		UserEntity user = new UserEntity(id, "user-" + id, "User " + id, "hash", false);
		for (UserRole role : roles) {
			user.addRole(role);
		}
		return user;
	}
}
