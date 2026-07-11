package moe.herz.verhaarmbackend.slushyrecipe;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import moe.herz.verhaarmbackend.audit.AuditLogRepository;
import moe.herz.verhaarmbackend.audit.AuditLogService;
import moe.herz.verhaarmbackend.slushyrecipe.dto.CreateSlushyRecipeRequest;
import moe.herz.verhaarmbackend.slushyrecipe.dto.IngredientRequest;
import moe.herz.verhaarmbackend.slushyrecipe.dto.RateSlushyRecipeRequest;
import moe.herz.verhaarmbackend.slushyrecipe.dto.SlushyRecipeDto;
import moe.herz.verhaarmbackend.slushyrecipe.dto.UpdateSlushyRecipeRequest;
import moe.herz.verhaarmbackend.user.UserEntity;
import moe.herz.verhaarmbackend.user.UserRepository;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SlushyRecipeServiceTest {

	@Mock
	private SlushyRecipeRepository recipes;
	@Mock
	private SlushyRecipeIngredientRepository ingredients;
	@Mock
	private SlushyRecipeRatingRepository ratings;
	@Mock
	private UserRepository users;
	@Mock
	private AuditLogRepository auditRepo;
	@Mock
	private EntityManager em;

	private SlushyRecipeService service;

	@BeforeEach
	void setUp() {
		AuditLogService audit = new AuditLogService(auditRepo, new ObjectMapper());
		service = new SlushyRecipeService(recipes, ingredients, ratings, users, audit);
		ReflectionTestUtils.setField(service, "em", em);
	}

	@Test
	void createSavesRecipeWithIngredientsAndReturnsDto() {
		UserEntity actor = user(UserRole.MEMBER);

		when(recipes.findVisibleById(any(UUID.class))).thenAnswer(inv ->
				Optional.of(new SlushyRecipeEntity(inv.getArgument(0), "Erdbeer Slushy", "Lecker", actor.getId())));
		when(ingredients.findByRecipeIdOrderBySortOrderAsc(any())).thenReturn(List.of());
		when(ratings.findByRecipeId(any())).thenReturn(List.of());
		when(ratings.averageStarsByRecipeId(any())).thenReturn(null);
		when(ratings.countByRecipeId(any())).thenReturn(0L);
		when(users.findAllById(anyCollection())).thenReturn(List.of(actor));

		SlushyRecipeDto dto = service.create(
				new CreateSlushyRecipeRequest(
						"Erdbeer Slushy",
						"Lecker",
						List.of(new IngredientRequest("Erdbeeren", "200g"), new IngredientRequest("Eis", null))
				),
				actor
		);

		assertEquals("Erdbeer Slushy", dto.title());
		assertEquals(actor.getId(), dto.createdByUserId());
		verify(ingredients, times(2)).save(any(SlushyRecipeIngredientEntity.class));
	}

	@Test
	void ownerCanUpdateOwnRecipe() {
		UserEntity owner = user(UserRole.MEMBER);
		SlushyRecipeEntity entity = new SlushyRecipeEntity(UUID.randomUUID(), "Alter Titel", "Alt", owner.getId());

		when(recipes.findVisibleById(entity.getId())).thenReturn(Optional.of(entity));
		when(ingredients.findByRecipeIdOrderBySortOrderAsc(entity.getId())).thenReturn(List.of());
		when(ratings.findByRecipeId(entity.getId())).thenReturn(List.of());
		when(ratings.averageStarsByRecipeId(entity.getId())).thenReturn(null);
		when(ratings.countByRecipeId(entity.getId())).thenReturn(0L);
		when(users.findAllById(anyCollection())).thenReturn(List.of(owner));

		SlushyRecipeDto dto = service.update(
				entity.getId(),
				new UpdateSlushyRecipeRequest("Neuer Titel", null, null),
				owner
		);

		assertEquals("Neuer Titel", dto.title());
		verify(recipes).save(entity);
		verify(ingredients, never()).deleteByRecipeId(any());
	}

	@Test
	void nonOwnerNonStaffCannotUpdateForeignRecipe() {
		UserEntity owner = user(UserRole.MEMBER);
		UserEntity stranger = user(UserRole.MEMBER);
		SlushyRecipeEntity entity = new SlushyRecipeEntity(UUID.randomUUID(), "Titel", "Beschreibung", owner.getId());

		when(recipes.findVisibleById(entity.getId())).thenReturn(Optional.of(entity));

		ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.update(
				entity.getId(),
				new UpdateSlushyRecipeRequest("Hack", null, null),
				stranger
		));

		assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
		verify(recipes, never()).save(any());
	}

	@Test
	void nonOwnerNonStaffCannotDeleteForeignRecipe() {
		UserEntity owner = user(UserRole.MEMBER);
		UserEntity stranger = user(UserRole.MEMBER);
		SlushyRecipeEntity entity = new SlushyRecipeEntity(UUID.randomUUID(), "Titel", "Beschreibung", owner.getId());

		when(recipes.findVisibleById(entity.getId())).thenReturn(Optional.of(entity));

		ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
				service.delete(entity.getId(), stranger));

		assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
		verify(recipes, never()).save(any());
	}

	@Test
	void staffRolesCanManageAnyRecipeRegardlessOfOwner() {
		for (UserRole staffRole : List.of(UserRole.SENIOR, UserRole.HOUSEKEEPING, UserRole.ADMIN)) {
			UserEntity owner = user(UserRole.MEMBER);
			UserEntity staff = user(staffRole);
			SlushyRecipeEntity entity = new SlushyRecipeEntity(UUID.randomUUID(), "Titel", "Beschreibung", owner.getId());

			when(recipes.findVisibleById(entity.getId())).thenReturn(Optional.of(entity));

			assertDoesNotThrow(() -> service.delete(entity.getId(), staff));
			assertTrue(entity.isDeleted());

			reset(recipes);
		}
	}

	@Test
	void deleteSoftDeletesRecipe() {
		UserEntity owner = user(UserRole.MEMBER);
		SlushyRecipeEntity entity = new SlushyRecipeEntity(UUID.randomUUID(), "Titel", "Beschreibung", owner.getId());

		when(recipes.findVisibleById(entity.getId())).thenReturn(Optional.of(entity));

		service.delete(entity.getId(), owner);

		assertNotNull(entity.getDeletedAt());
		verify(recipes).save(entity);
	}

	@Test
	void ratingUpsertOverwritesPreviousRatingForSameUser() {
		UserEntity actor = user(UserRole.MEMBER);
		SlushyRecipeEntity entity = new SlushyRecipeEntity(UUID.randomUUID(), "Titel", "Beschreibung", actor.getId());

		when(recipes.findVisibleById(entity.getId())).thenReturn(Optional.of(entity));
		when(ingredients.findByRecipeIdOrderBySortOrderAsc(entity.getId())).thenReturn(List.of());
		when(ratings.findByRecipeId(entity.getId())).thenReturn(List.of());
		when(ratings.averageStarsByRecipeId(entity.getId())).thenReturn(3.0);
		when(ratings.countByRecipeId(entity.getId())).thenReturn(1L);
		when(users.findAllById(anyCollection())).thenReturn(List.of(actor));
		when(ratings.findByRecipeIdAndUserId(entity.getId(), actor.getId())).thenReturn(Optional.empty());

		service.rate(entity.getId(), new RateSlushyRecipeRequest(3, "Ganz ok"), actor);

		ArgumentCaptor<SlushyRecipeRatingEntity> captor = ArgumentCaptor.forClass(SlushyRecipeRatingEntity.class);
		verify(ratings).save(captor.capture());
		SlushyRecipeRatingEntity saved = captor.getValue();
		assertEquals(3, saved.getStars());

		reset(ratings);
		when(ratings.findByRecipeIdAndUserId(entity.getId(), actor.getId())).thenReturn(Optional.of(saved));
		when(ratings.findByRecipeId(entity.getId())).thenReturn(List.of(saved));
		when(ratings.averageStarsByRecipeId(entity.getId())).thenReturn(5.0);
		when(ratings.countByRecipeId(entity.getId())).thenReturn(1L);

		SlushyRecipeDto dto = service.rate(entity.getId(), new RateSlushyRecipeRequest(5, "Sehr gut"), actor);

		verify(ratings).save(saved);
		assertEquals(5, saved.getStars());
		assertEquals("Sehr gut", saved.getComment());
		assertEquals(5.0, dto.ratingSummary().average());
		assertEquals(1, dto.ratingSummary().count());
		assertEquals(5, dto.ratingSummary().myStars());
	}

	private static UserEntity user(UserRole... roles) {
		UUID id = UUID.randomUUID();
		UserEntity user = new UserEntity(id, "user-" + id, "User " + id, "hash", false);
		for (UserRole role : roles) {
			user.addRole(role);
		}
		return user;
	}
}
