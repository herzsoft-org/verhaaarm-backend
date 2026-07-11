package moe.herz.verhaarmbackend.slushyrecipe;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import moe.herz.verhaarmbackend.audit.AuditLogService;
import moe.herz.verhaarmbackend.common.ApiErrors;
import moe.herz.verhaarmbackend.slushyrecipe.dto.CreateSlushyRecipeRequest;
import moe.herz.verhaarmbackend.slushyrecipe.dto.IngredientDto;
import moe.herz.verhaarmbackend.slushyrecipe.dto.IngredientRequest;
import moe.herz.verhaarmbackend.slushyrecipe.dto.RateSlushyRecipeRequest;
import moe.herz.verhaarmbackend.slushyrecipe.dto.RatingDto;
import moe.herz.verhaarmbackend.slushyrecipe.dto.RatingSummaryDto;
import moe.herz.verhaarmbackend.slushyrecipe.dto.SlushyRecipeDto;
import moe.herz.verhaarmbackend.slushyrecipe.dto.UpdateSlushyRecipeRequest;
import moe.herz.verhaarmbackend.user.UserEntity;
import moe.herz.verhaarmbackend.user.UserRepository;
import moe.herz.verhaarmbackend.user.UserRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SlushyRecipeService {

	private final SlushyRecipeRepository recipes;
	private final SlushyRecipeIngredientRepository ingredients;
	private final SlushyRecipeRatingRepository ratings;
	private final UserRepository users;
	private final AuditLogService audit;

	@PersistenceContext
	private EntityManager em;

	public SlushyRecipeService(
			SlushyRecipeRepository recipes,
			SlushyRecipeIngredientRepository ingredients,
			SlushyRecipeRatingRepository ratings,
			UserRepository users,
			AuditLogService audit
	) {
		this.recipes = recipes;
		this.ingredients = ingredients;
		this.ratings = ratings;
		this.users = users;
		this.audit = audit;
	}

	@Transactional(readOnly = true)
	public List<SlushyRecipeDto> list(UserEntity actor) {
		return recipes.findAllVisible().stream().map(r -> toDto(r, actor)).toList();
	}

	@Transactional(readOnly = true)
	public SlushyRecipeDto get(UUID id, UserEntity actor) {
		SlushyRecipeEntity r = recipes.findVisibleById(id)
				.orElseThrow(() -> ApiErrors.notFound("Recipe not found"));
		return toDto(r, actor);
	}

	@Transactional
	public SlushyRecipeDto create(CreateSlushyRecipeRequest req, UserEntity actor) {
		if (actor == null) throw ApiErrors.forbidden("Forbidden");

		String title = (req.title() == null) ? "" : req.title().trim();
		if (title.isBlank()) throw ApiErrors.badRequest("Title required");

		String description = (req.description() == null) ? null : req.description().trim();

		SlushyRecipeEntity r = new SlushyRecipeEntity(UUID.randomUUID(), title, description, actor.getId());
		recipes.save(r);

		saveIngredients(r.getId(), req.ingredients());

		em.flush();
		em.clear();

		SlushyRecipeEntity reloaded = recipes.findVisibleById(r.getId())
				.orElseThrow(() -> ApiErrors.notFound("Recipe not found"));

		var d = audit.obj();
		audit.put(d, "recipeId", reloaded.getId());
		audit.put(d, "title", reloaded.getTitle());
		audit.log(actor, "slushyRecipe.create", d);

		return toDto(reloaded, actor);
	}

	@Transactional
	public SlushyRecipeDto update(UUID id, UpdateSlushyRecipeRequest req, UserEntity actor) {
		if (actor == null) throw ApiErrors.forbidden("Forbidden");

		SlushyRecipeEntity r = recipes.findVisibleById(id)
				.orElseThrow(() -> ApiErrors.notFound("Recipe not found"));

		if (!canManage(actor, r)) throw ApiErrors.forbidden("Forbidden");

		String beforeTitle = r.getTitle();
		String beforeDescription = r.getDescription();

		if (req.title() != null) {
			String title = req.title().trim();
			if (title.isBlank()) throw ApiErrors.badRequest("Title required");
			r.setTitle(title);
		}

		if (req.description() != null) {
			String description = req.description().trim();
			r.setDescription(description.isBlank() ? null : description);
		}

		recipes.save(r);

		if (req.ingredients() != null) {
			ingredients.deleteByRecipeId(r.getId());
			saveIngredients(r.getId(), req.ingredients());
		}

		em.flush();
		em.clear();

		SlushyRecipeEntity reloaded = recipes.findVisibleById(r.getId())
				.orElseThrow(() -> ApiErrors.notFound("Recipe not found"));

		var d = audit.obj();
		audit.put(d, "recipeId", reloaded.getId());

		var before = audit.obj();
		audit.put(before, "title", beforeTitle);
		audit.put(before, "description", beforeDescription);

		var after = audit.obj();
		audit.put(after, "title", reloaded.getTitle());
		audit.put(after, "description", reloaded.getDescription());

		d.set("before", before);
		d.set("after", after);

		audit.log(actor, "slushyRecipe.update", d);

		return toDto(reloaded, actor);
	}

	@Transactional
	public void delete(UUID id, UserEntity actor) {
		if (actor == null) throw ApiErrors.forbidden("Forbidden");

		SlushyRecipeEntity r = recipes.findVisibleById(id)
				.orElseThrow(() -> ApiErrors.notFound("Recipe not found"));

		if (!canManage(actor, r)) throw ApiErrors.forbidden("Forbidden");

		r.setDeletedAt(java.time.OffsetDateTime.now());
		recipes.save(r);

		var d = audit.obj();
		audit.put(d, "recipeId", r.getId());
		audit.put(d, "title", r.getTitle());
		audit.log(actor, "slushyRecipe.delete", d);
	}

	@Transactional
	public SlushyRecipeDto rate(UUID id, RateSlushyRecipeRequest req, UserEntity actor) {
		if (actor == null) throw ApiErrors.forbidden("Forbidden");

		SlushyRecipeEntity r = recipes.findVisibleById(id)
				.orElseThrow(() -> ApiErrors.notFound("Recipe not found"));

		if (req.stars() == null || req.stars() < 1 || req.stars() > 5) {
			throw ApiErrors.badRequest("Stars must be between 1 and 5");
		}

		String comment = (req.comment() == null) ? null : req.comment().trim();
		if (comment != null && comment.isBlank()) comment = null;

		SlushyRecipeRatingEntity rating = ratings.findByRecipeIdAndUserId(r.getId(), actor.getId())
				.orElse(null);

		if (rating == null) {
			rating = new SlushyRecipeRatingEntity(UUID.randomUUID(), r.getId(), actor.getId(), req.stars(), comment);
		} else {
			rating.setStars(req.stars());
			rating.setComment(comment);
		}

		ratings.save(rating);

		em.flush();
		em.clear();

		var d = audit.obj();
		audit.put(d, "recipeId", r.getId());
		audit.put(d, "stars", req.stars());
		audit.log(actor, "slushyRecipe.rate", d);

		SlushyRecipeEntity reloaded = recipes.findVisibleById(r.getId())
				.orElseThrow(() -> ApiErrors.notFound("Recipe not found"));

		return toDto(reloaded, actor);
	}

	private void saveIngredients(UUID recipeId, List<IngredientRequest> reqIngredients) {
		if (reqIngredients == null) return;

		int order = 0;
		for (IngredientRequest ing : reqIngredients) {
			if (ing == null || ing.name() == null) continue;
			String name = ing.name().trim();
			if (name.isBlank()) continue;

			String amount = (ing.amount() == null) ? null : ing.amount().trim();
			if (amount != null && amount.isBlank()) amount = null;

			ingredients.save(new SlushyRecipeIngredientEntity(UUID.randomUUID(), recipeId, name, amount, order));
			order++;
		}
	}

	private static boolean hasRole(UserEntity u, UserRole role) {
		return u != null && u.getRoles().stream().anyMatch(r -> r.getRole() == role);
	}

	private static boolean isStaff(UserEntity u) {
		return hasRole(u, UserRole.ADMIN) || hasRole(u, UserRole.SENIOR) || hasRole(u, UserRole.HOUSEKEEPING);
	}

	private static boolean isCreator(UserEntity actor, SlushyRecipeEntity r) {
		return actor != null
				&& actor.getId() != null
				&& r.getCreatedByUserId() != null
				&& r.getCreatedByUserId().equals(actor.getId());
	}

	private static boolean canManage(UserEntity actor, SlushyRecipeEntity r) {
		return isStaff(actor) || isCreator(actor, r);
	}

	private SlushyRecipeDto toDto(SlushyRecipeEntity r, UserEntity actor) {
		List<IngredientDto> ingredientDtos = ingredients.findByRecipeIdOrderBySortOrderAsc(r.getId())
				.stream()
				.map(i -> new IngredientDto(i.getId(), i.getName(), i.getAmount()))
				.toList();

		List<SlushyRecipeRatingEntity> ratingEntities = ratings.findByRecipeId(r.getId());

		Map<UUID, String> displayNames = new LinkedHashMap<>();
		List<UUID> lookupIds = new ArrayList<>();
		if (r.getCreatedByUserId() != null) lookupIds.add(r.getCreatedByUserId());
		for (SlushyRecipeRatingEntity ratingEntity : ratingEntities) lookupIds.add(ratingEntity.getUserId());

		if (!lookupIds.isEmpty()) {
			for (UserEntity u : users.findAllById(lookupIds)) {
				displayNames.put(u.getId(), u.getDisplayName());
			}
		}

		List<RatingDto> ratingDtos = ratingEntities.stream()
				.map(ratingEntity -> new RatingDto(
						ratingEntity.getUserId(),
						displayNames.getOrDefault(ratingEntity.getUserId(), "Unbekannt"),
						ratingEntity.getStars(),
						ratingEntity.getComment(),
						ratingEntity.getCreatedAt(),
						ratingEntity.getUpdatedAt()
				))
				.toList();

		Double avg = ratings.averageStarsByRecipeId(r.getId());
		long count = ratings.countByRecipeId(r.getId());

		Integer myStars = null;
		String myComment = null;
		if (actor != null) {
			SlushyRecipeRatingEntity mine = ratingEntities.stream()
					.filter(ratingEntity -> ratingEntity.getUserId().equals(actor.getId()))
					.findFirst()
					.orElse(null);
			if (mine != null) {
				myStars = mine.getStars();
				myComment = mine.getComment();
			}
		}

		RatingSummaryDto summary = new RatingSummaryDto(avg == null ? 0.0 : avg, (int) count, myStars, myComment);

		return new SlushyRecipeDto(
				r.getId(),
				r.getTitle(),
				r.getDescription(),
				ingredientDtos,
				r.getCreatedByUserId(),
				r.getCreatedByUserId() == null ? null : displayNames.get(r.getCreatedByUserId()),
				r.getCreatedAt(),
				r.getUpdatedAt(),
				summary,
				ratingDtos
		);
	}
}
