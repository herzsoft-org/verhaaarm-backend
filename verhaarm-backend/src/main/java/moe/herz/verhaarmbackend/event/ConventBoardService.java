package moe.herz.verhaarmbackend.event;

import moe.herz.verhaarmbackend.audit.AuditLogService;
import moe.herz.verhaarmbackend.common.ApiErrors;
import moe.herz.verhaarmbackend.common.StructuredApiError;
import moe.herz.verhaarmbackend.event.dto.ConventBoardChangeDto;
import moe.herz.verhaarmbackend.event.dto.ConventBoardCreateDto;
import moe.herz.verhaarmbackend.event.dto.ConventBoardDto;
import moe.herz.verhaarmbackend.event.dto.ConventBoardItemDto;
import moe.herz.verhaarmbackend.event.dto.ConventBoardSemesterDto;
import moe.herz.verhaarmbackend.event.dto.UpdateConventBoardRequest;
import moe.herz.verhaarmbackend.period.ConventDerivation;
import moe.herz.verhaarmbackend.periodprotocol.ConventPeriodProtocolRepository;
import moe.herz.verhaarmbackend.user.UserEntity;
import moe.herz.verhaarmbackend.user.UserRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The "Convente" management board: every Convent-flagged event, grouped into semester blocks, with
 * an atomic batch endpoint for reordering/retyping/redating, creating and deleting several Convente
 * at once.
 * <p>
 * This exists because real repairs to a broken sequence (e.g. a Convent migrated with the wrong
 * type, or a missing Convent that needs to be inserted between two existing ones) often require
 * touching more than one Convent together - an intermediate single-step state can be invalid even
 * though the final coordinated result is valid. The single-Convent {@link EventService#update}/
 * {@link EventService#delete} paths validate every write in isolation, so they can permanently
 * deadlock a repair that genuinely needs several coordinated changes; this batch validates only the
 * final proposed timeline, once, and applies all-or-nothing.
 */
@Service
public class ConventBoardService {

	private static final ZoneId ZONE_BERLIN = ZoneId.of("Europe/Berlin");

	private final EventRepository events;
	private final ConventPeriodProtocolRepository protocols;
	private final AuditLogService audit;
	private final EventService eventService;

	public ConventBoardService(
			EventRepository events,
			ConventPeriodProtocolRepository protocols,
			AuditLogService audit,
			EventService eventService
	) {
		this.events = events;
		this.protocols = protocols;
		this.audit = audit;
		this.eventService = eventService;
	}

	@Transactional(readOnly = true)
	public ConventBoardDto board(UserEntity actor) {
		requireAdminOrSenior(actor);

		List<EventEntity> conventEvents = events.findAllConventsOrderedVisible();
		Map<UUID, EventEntity> byId = conventEvents.stream()
				.collect(Collectors.toMap(EventEntity::getId, e -> e));
		Map<UUID, OffsetDateTime> displayStartsAt = conventEvents.stream()
				.collect(Collectors.toMap(EventEntity::getId, EventEntity::getStartsAt));

		List<ConventDerivation.ConventRef> refs = conventEvents.stream().map(ConventBoardService::toRef).toList();
		Set<UUID> protocolIds = new HashSet<>(protocols.findAllPeriodIds());

		return projectBoard(refs, byId, displayStartsAt, protocolIds);
	}

	/** Would the batch succeed? Throws the same structured error `apply` would; returns nothing on success. */
	@Transactional(readOnly = true)
	public void validateBatch(UpdateConventBoardRequest req, UserEntity actor) {
		requireAdminOrSenior(actor);
		BatchPlan plan = buildPlan(req, false);
		runValidation(plan);
	}

	@Transactional
	public ConventBoardDto apply(UpdateConventBoardRequest req, UserEntity actor) {
		requireAdminOrSenior(actor);
		// Pessimistic write lock on every Convent row for the rest of this transaction: two
		// concurrent board saves must not both validate against the same pre-write timeline and
		// then commit a combined result neither of them actually checked.
		BatchPlan plan = buildPlan(req, true);
		runValidation(plan);

		for (ConventBoardChangeDto change : req.changes()) {
			EventEntity e = plan.byId().get(change.eventId());

			OffsetDateTime beforeStartsAt = e.getStartsAt();
			ConventType beforeType = e.getConventType();
			boolean anyChange = !beforeStartsAt.isEqual(change.startsAt()) || beforeType != change.conventType();

			// The board always resubmits every Convent, not just a computed diff - skip save/audit
			// entirely for true no-ops, or every save would flood the audit log with before==after
			// entries for everything the user didn't touch.
			if (!anyChange) continue;

			if (!beforeStartsAt.isEqual(change.startsAt())) {
				e.setStartsAt(change.startsAt());
			}
			if (beforeType != change.conventType()) {
				e.setConventType(change.conventType());
			}
			events.save(e);

			var d = audit.obj();
			audit.put(d, "eventId", e.getId());
			audit.put(d, "beforeStartsAt", beforeStartsAt.toString());
			audit.put(d, "beforeConventType", beforeType == null ? null : beforeType.name());
			audit.put(d, "afterStartsAt", e.getStartsAt().toString());
			audit.put(d, "afterConventType", e.getConventType() == null ? null : e.getConventType().name());
			audit.log(actor, "event.conventBoard.update", d);
		}

		for (UUID id : plan.deleteIds()) {
			EventEntity e = plan.byId().get(id);

			eventService.softDeleteAndCleanup(e);

			var d = audit.obj();
			audit.put(d, "eventId", e.getId());
			audit.put(d, "title", e.getTitle());
			audit.put(d, "conventType", e.getConventType() == null ? null : e.getConventType().name());
			audit.log(actor, "event.conventBoard.delete", d);
		}

		for (Map.Entry<UUID, ConventBoardCreateDto> entry : plan.creates().entrySet()) {
			UUID newId = entry.getKey();
			ConventBoardCreateDto create = entry.getValue();

			String title = create.title() == null ? "" : create.title().trim();
			if (title.isBlank()) throw ApiErrors.badRequest("Title required");
			String location = EventService.normalizeLocation(create.location());
			boolean mandatory = create.mandatory() == null || create.mandatory();

			EventEntity e = new EventEntity(
					newId,
					actor.getId(),
					title,
					location,
					create.startsAt(),
					mandatory,
					EventKind.MAIN,
					EventOwnerType.SENIOR
			);
			e.setConventType(create.conventType());
			events.save(e);

			var d = audit.obj();
			audit.put(d, "eventId", e.getId());
			audit.put(d, "title", e.getTitle());
			audit.put(d, "location", e.getLocation());
			audit.put(d, "startsAt", e.getStartsAt().toString());
			audit.put(d, "conventType", e.getConventType().name());
			audit.log(actor, "event.conventBoard.create", d);
		}

		return board(actor);
	}

	private record BatchPlan(
			Map<UUID, EventEntity> byId,
			List<ConventDerivation.ConventRef> before,
			List<ConventDerivation.ConventRef> after,
			Set<UUID> targetIds,
			Set<UUID> deleteIds,
			Map<UUID, ConventBoardCreateDto> creates
	) {}

	private BatchPlan buildPlan(UpdateConventBoardRequest req, boolean forUpdate) {
		if (req.changes().isEmpty() && req.creates().isEmpty() && req.deleteEventIds().isEmpty()) {
			throw ApiErrors.badRequest("The batch must contain at least one change, create or delete");
		}

		List<EventEntity> conventEvents = forUpdate
				? events.findAllConventsOrderedVisibleForUpdate()
				: events.findAllConventsOrderedVisible();
		Map<UUID, EventEntity> byId = conventEvents.stream()
				.collect(Collectors.toMap(EventEntity::getId, e -> e));

		List<ConventDerivation.ConventRef> before = conventEvents.stream().map(ConventBoardService::toRef).toList();

		Map<UUID, ConventDerivation.ConventRef> overrides = new LinkedHashMap<>();
		Set<UUID> targetIds = new HashSet<>();
		Set<UUID> seen = new HashSet<>();

		for (ConventBoardChangeDto change : req.changes()) {
			if (!seen.add(change.eventId())) {
				throw ApiErrors.badRequest("Duplicate eventId in changes: " + change.eventId());
			}

			EventEntity e = byId.get(change.eventId());
			if (e == null) {
				// Not in the convent-only map - disambiguate "doesn't exist" from "exists but isn't
				// a Convent" for a clearer error message (byId only ever contains convent_type IS
				// NOT NULL rows, so this lookup is intentionally separate from the happy path).
				EventEntity anyEvent = events.findVisibleById(change.eventId()).orElse(null);
				if (anyEvent == null) {
					throw ApiErrors.notFound("Convent not found: " + change.eventId());
				}
				throw ApiErrors.badRequest("Event " + change.eventId() + " is not a Convent - use the normal Event endpoints to mark it as one first");
			}

			LocalDate newDate = toBerlinDate(change.startsAt());
			LocalDate currentDate = toBerlinDate(e.getStartsAt());
			boolean structuralChange = !newDate.equals(currentDate) || change.conventType() != e.getConventType();

			overrides.put(change.eventId(), new ConventDerivation.ConventRef(change.eventId(), newDate, change.conventType()));
			if (structuralChange) targetIds.add(change.eventId());
		}

		Set<UUID> deleteIds = new HashSet<>();
		for (UUID id : req.deleteEventIds()) {
			if (!deleteIds.add(id)) continue; // duplicate delete id, harmless

			if (overrides.containsKey(id)) {
				throw ApiErrors.badRequest("Event " + id + " is in both changes and deleteEventIds");
			}

			EventEntity e = byId.get(id);
			if (e == null) {
				EventEntity anyEvent = events.findVisibleById(id).orElse(null);
				if (anyEvent == null) {
					throw ApiErrors.notFound("Convent not found: " + id);
				}
				throw ApiErrors.badRequest("Event " + id + " is not a Convent - use the normal Event endpoints to delete it");
			}
		}

		Map<UUID, ConventBoardCreateDto> createById = new LinkedHashMap<>();
		List<ConventDerivation.ConventRef> newRefs = new ArrayList<>();
		for (ConventBoardCreateDto create : req.creates()) {
			UUID newId = UUID.randomUUID();
			LocalDate d = toBerlinDate(create.startsAt());
			createById.put(newId, create);
			newRefs.add(new ConventDerivation.ConventRef(newId, d, create.conventType()));
			targetIds.add(newId); // a brand-new Convent must always land in a consistent spot
		}

		List<ConventDerivation.ConventRef> after = new ArrayList<>();
		for (ConventDerivation.ConventRef ref : before) {
			if (deleteIds.contains(ref.id())) continue;
			after.add(overrides.getOrDefault(ref.id(), ref));
		}
		after.addAll(newRefs);
		after.sort(Comparator.comparing(ConventDerivation.ConventRef::date));

		return new BatchPlan(byId, before, after, targetIds, deleteIds, createById);
	}

	private void runValidation(BatchPlan plan) {
		Set<UUID> allProtocolIds = new HashSet<>(protocols.findAllPeriodIds());

		// Checked directly (not just via validateProtocolsUnaffected below) for a clear, delete-specific
		// message - matching the single-Event delete flow's wording - rather than the generic
		// "range would change" message that a vanished period would otherwise produce.
		for (UUID id : plan.deleteIds()) {
			if (allProtocolIds.contains(id)) {
				throw StructuredApiError.badRequest(
						"CONVENT_HAS_PROTOCOL",
						"This Convent already has an uploaded Protokoll. Remove the Protokoll before deleting it.",
						StructuredApiError.details("conventId", id, "action", "delete")
				);
			}
		}

		ConventDerivation.validateNoRegression(plan.before(), plan.after(), plan.targetIds());

		Set<UUID> protocolIdsToPreserve = new HashSet<>(allProtocolIds);
		protocolIdsToPreserve.removeAll(plan.deleteIds()); // already rejected above with a clearer message
		ConventDerivation.validateProtocolsUnaffected(plan.before(), plan.after(), protocolIdsToPreserve);
	}

	private ConventBoardDto projectBoard(
			List<ConventDerivation.ConventRef> refs,
			Map<UUID, EventEntity> byId,
			Map<UUID, OffsetDateTime> displayStartsAt,
			Set<UUID> protocolIds
	) {
		LinkedHashMap<String, List<ConventBoardItemDto>> bySemester = new LinkedHashMap<>();

		for (ConventDerivation.DerivedPeriod p : ConventDerivation.derive(refs)) {
			if (p.id() == null) continue; // trailing OPEN/OPEN_SEMESTER_BREAK tail, not a real convent
			EventEntity e = byId.get(p.id());
			if (e == null) continue;

			ConventBoardItemDto item = new ConventBoardItemDto(
					p.id(),
					e.getTitle(),
					e.getLocation(),
					displayStartsAt.getOrDefault(p.id(), e.getStartsAt()),
					p.endingConventType(),
					p.endingConventLabel(),
					p.consistent(),
					p.warning(),
					protocolIds.contains(p.id())
			);
			bySemester.computeIfAbsent(p.semester(), k -> new ArrayList<>()).add(item);
		}

		List<ConventBoardSemesterDto> semesters = bySemester.entrySet().stream()
				.map(en -> new ConventBoardSemesterDto(en.getKey(), en.getValue()))
				.toList();

		return new ConventBoardDto(semesters);
	}

	private static void requireAdminOrSenior(UserEntity actor) {
		boolean ok = actor != null && actor.getRoles().stream()
				.anyMatch(r -> r.getRole() == UserRole.ADMIN || r.getRole() == UserRole.SENIOR);
		if (!ok) throw ApiErrors.forbidden("Only ADMIN/SENIOR can manage the Convente board");
	}

	private static ConventDerivation.ConventRef toRef(EventEntity e) {
		return new ConventDerivation.ConventRef(e.getId(), toBerlinDate(e.getStartsAt()), e.getConventType());
	}

	private static LocalDate toBerlinDate(OffsetDateTime t) {
		return t.atZoneSameInstant(ZONE_BERLIN).toLocalDate();
	}
}
