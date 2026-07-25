package moe.herz.verhaarmbackend.event;

import moe.herz.verhaarmbackend.audit.AuditLogService;
import moe.herz.verhaarmbackend.common.ApiErrors;
import moe.herz.verhaarmbackend.common.StructuredApiError;
import moe.herz.verhaarmbackend.event.dto.CreateEventRequest;
import moe.herz.verhaarmbackend.event.dto.EventDto;
import moe.herz.verhaarmbackend.event.dto.UpdateEventRequest;
import moe.herz.verhaarmbackend.period.ConventDerivation;
import moe.herz.verhaarmbackend.periodprotocol.ConventPeriodProtocolRepository;
import moe.herz.verhaarmbackend.user.UserEntity;
import moe.herz.verhaarmbackend.user.UserRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class EventService {

	private static final ZoneId ZONE_BERLIN = ZoneId.of("Europe/Berlin");
	private static final String DEFAULT_LOCATION = "adH";
	private static final long CONVENT_WRITE_LOCK_KEY = 847362910473L;

	private final EventRepository events;
	private final AuditLogService audit;
	private final ConventPeriodProtocolRepository protocols;

	@PersistenceContext
	private EntityManager em;

	public EventService(EventRepository events, AuditLogService audit, ConventPeriodProtocolRepository protocols) {
		this.events = events;
		this.audit = audit;
		this.protocols = protocols;
	}

	@Transactional(readOnly = true)
	public List<EventDto> listVisible(UserEntity actor) {
		List<EventEntity> all = events.findAllVisible();
		Map<UUID, String> labels = conventLabels();
		return all.stream().map(e -> toDto(e, labels)).toList();
	}

	@Transactional(readOnly = true)
	public EventDto getVisible(UUID id, UserEntity actor) {
		var e = events.findVisibleById(id).orElseThrow(() -> ApiErrors.notFound("Event not found"));
		return toDto(e, conventLabels());
	}

	@Transactional
	public EventDto create(CreateEventRequest req, UserEntity actor) {
		if (!(hasRole(actor, UserRole.ADMIN) || hasRole(actor, UserRole.SENIOR) || hasRole(actor, UserRole.HOUSEKEEPING))) {
			throw ApiErrors.forbidden("Forbidden");
		}

		String title = req.title() == null ? "" : req.title().trim();
		if (title.isBlank()) throw ApiErrors.badRequest("Title required");

		String location = normalizeLocation(req.location());

		OffsetDateTime startsAt = req.startsAt();
		if (startsAt == null) throw ApiErrors.badRequest("startsAt required");

		boolean isAdmin = hasRole(actor, UserRole.ADMIN);
		boolean isSenior = hasRole(actor, UserRole.SENIOR);

		// Convents are sometimes entered after the fact (fixing/backfilling history) - everything
		// else keeps the normal "no past events" rule.
		boolean pastDateAllowed = req.conventType() != null && (isAdmin || isSenior);
		if (!pastDateAllowed && startsAt.isBefore(OffsetDateTime.now())) {
			throw ApiErrors.badRequest("Cannot schedule events in the past");
		}

		boolean mandatory = req.mandatory() != null && req.mandatory();
		EventKind eventKind = req.eventKind() == null ? EventKind.MAIN : req.eventKind();

		EventOwnerType ownerType = (isAdmin || isSenior) ? EventOwnerType.SENIOR : EventOwnerType.HOUSEKEEPING;

		UUID id = UUID.randomUUID();

		if (req.conventType() != null) {
			if (!(isAdmin || isSenior)) {
				throw ApiErrors.forbidden("Only ADMIN/SENIOR can mark an event as a Convent");
			}
			acquireConventWriteLock();
			validateConventChange(null, new ConventDerivation.ConventRef(id, toBerlinDate(startsAt), req.conventType()));
		}

		var e = new EventEntity(
				id,
				actor.getId(),
				title,
				location,
				startsAt,
				mandatory,
				eventKind,
				ownerType
		);
		e.setConventType(req.conventType());

		events.save(e);

		em.flush();
		em.clear();

		var reloaded = events.findVisibleById(e.getId())
				.orElseThrow(() -> ApiErrors.notFound("Event not found"));

		var d = audit.obj();
		audit.put(d, "eventId", reloaded.getId());
		audit.put(d, "creatorUserId", reloaded.getCreatorUserId());
		audit.put(d, "title", reloaded.getTitle());
		audit.put(d, "location", reloaded.getLocation());
		audit.put(d, "startsAt", reloaded.getStartsAt() == null ? null : reloaded.getStartsAt().toString());
		audit.put(d, "mandatory", reloaded.isMandatory());
		audit.put(d, "eventKind", reloaded.getEventKind() == null ? null : reloaded.getEventKind().name());
		audit.put(d, "ownerType", reloaded.getOwnerType() == null ? null : reloaded.getOwnerType().name());
		audit.put(d, "conventType", reloaded.getConventType() == null ? null : reloaded.getConventType().name());
		audit.log(actor, "event.create", d);

		return toDto(reloaded, conventLabels());
	}

	@Transactional
	public EventDto update(UUID id, UpdateEventRequest req, UserEntity actor) {
		// Must happen before the entity is read, not just before validateConventChange: once this
		// blocks on a board (or another update/delete) transaction that's already holding the lock,
		// `e` would otherwise already be loaded from a stale pre-commit snapshot by the time we
		// resume - a same-Berlin-date "unchanged" field could then silently revert whatever the other
		// transaction just committed, or act on a row it just soft-deleted. Whether this update
		// *could* be structural is knowable purely from the request, before `e` is even read: a
		// structural change always requires touching startsAt, conventType or clearConventType.
		boolean mayBeStructural = req.startsAt() != null
				|| req.conventType() != null
				|| Boolean.TRUE.equals(req.clearConventType());
		if (mayBeStructural) acquireConventWriteLock();

		var e = events.findVisibleById(id).orElseThrow(() -> ApiErrors.notFound("Event not found"));

		boolean isAdmin = hasRole(actor, UserRole.ADMIN);
		boolean isSenior = hasRole(actor, UserRole.SENIOR);
		boolean isHousekeeping = hasRole(actor, UserRole.HOUSEKEEPING);

		if (!(isAdmin || isSenior || isHousekeeping)) throw ApiErrors.forbidden("Forbidden");

		if (!isAdmin && !isSenior) {
			if (e.getOwnerType() != EventOwnerType.HOUSEKEEPING) {
				throw ApiErrors.forbidden("HOUSEKEEPING can only edit HOUSEKEEPING events");
			}
			if (!e.getCreatorUserId().equals(actor.getId())) {
				throw ApiErrors.forbidden("HOUSEKEEPING can only edit own events");
			}
			// A Convent-flagged event stays ADMIN/SENIOR-only regardless of who owns/created it -
			// e.g. a HOUSEKEEPING-owned event tagged as a Convent by the legacy-data migration must
			// not become editable/movable by its original HOUSEKEEPING creator.
			if (e.getConventType() != null || req.conventType() != null || Boolean.TRUE.equals(req.clearConventType())) {
				throw ApiErrors.forbidden("Only ADMIN/SENIOR can manage a Convent-flagged event");
			}
		}

		String beforeTitle = e.getTitle();
		String beforeLocation = e.getLocation();
		OffsetDateTime beforeStartsAt = e.getStartsAt();
		boolean beforeMandatory = e.isMandatory();
		EventKind beforeEventKind = e.getEventKind();
		ConventType beforeConventType = e.getConventType();

		if (req.title() != null) {
			String title = req.title().trim();
			if (title.isBlank()) throw ApiErrors.badRequest("Title required");
			e.setTitle(title);
		}

		if (req.location() != null) {
			e.setLocation(normalizeLocation(req.location()));
		}

		boolean clearConvent = Boolean.TRUE.equals(req.clearConventType());
		ConventType newConventType = clearConvent
				? null
				: (req.conventType() != null ? req.conventType() : beforeConventType);

		// Convents are sometimes entered/corrected after the fact - everything else keeps the
		// normal "no past events" rule.
		boolean pastDateAllowed = (isAdmin || isSenior) && (beforeConventType != null || req.conventType() != null);

		OffsetDateTime newStartsAt = beforeStartsAt;
		if (req.startsAt() != null) {
			if (!pastDateAllowed && req.startsAt().isBefore(OffsetDateTime.now())) {
				throw ApiErrors.badRequest("Cannot schedule events in the past");
			}
			newStartsAt = req.startsAt();
		}

		boolean wasConvent = beforeConventType != null;
		boolean isStillConvent = newConventType != null;
		boolean typeChanged = newConventType != beforeConventType;

		// A period is derived purely from local Berlin *dates* and convent *type* - a time-only
		// change within the same Berlin day, or a title/mandatory/eventKind-only edit, structurally
		// changes nothing, so it must not be blocked by an existing Protokoll or re-run timeline
		// validation. Without this, an already-inconsistent migrated Convent could never even have
		// its title fixed: the target would stay inconsistent (unchanged) and get rejected forever.
		LocalDate newBerlinDate = toBerlinDate(newStartsAt);
		boolean dateChanged = !newBerlinDate.equals(toBerlinDate(beforeStartsAt));
		boolean structuralChange = dateChanged || typeChanged;

		if (wasConvent && structuralChange && protocols.existsByPeriodId(id)) {
			throw StructuredApiError.badRequest(
					"CONVENT_HAS_PROTOCOL",
					"This Convent already has an uploaded Protokoll. Remove the Protokoll before moving, "
							+ "retyping or un-marking it.",
					StructuredApiError.details("conventId", id, "action", "update")
			);
		}

		if (isStillConvent && structuralChange) {
			// mayBeStructural (checked before `e` was even read, see above) is a superset of
			// structuralChange, so the lock is already held here.
			validateConventChange(id, new ConventDerivation.ConventRef(id, newBerlinDate, newConventType));
		} else if (!isStillConvent && wasConvent) {
			// un-marking is always structural: the remaining convents must still resolve to valid
			// semester blocks, and no other Convent's already-protocolled period may shift as a result.
			validateConventChange(id, null);
		}

		e.setStartsAt(newStartsAt);
		e.setConventType(newConventType);

		if (req.mandatory() != null) {
			e.setMandatory(req.mandatory());
		}

		if (req.eventKind() != null) {
			e.setEventKind(req.eventKind());
		}

		events.save(e);

		var d = audit.obj();
		audit.put(d, "eventId", e.getId());

		var before = audit.obj();
		audit.put(before, "title", beforeTitle);
		audit.put(before, "location", beforeLocation);
		audit.put(before, "startsAt", beforeStartsAt == null ? null : beforeStartsAt.toString());
		audit.put(before, "mandatory", beforeMandatory);
		audit.put(before, "eventKind", beforeEventKind == null ? null : beforeEventKind.name());
		audit.put(before, "conventType", beforeConventType == null ? null : beforeConventType.name());

		var after = audit.obj();
		audit.put(after, "title", e.getTitle());
		audit.put(after, "location", e.getLocation());
		audit.put(after, "startsAt", e.getStartsAt() == null ? null : e.getStartsAt().toString());
		audit.put(after, "mandatory", e.isMandatory());
		audit.put(after, "eventKind", e.getEventKind() == null ? null : e.getEventKind().name());
		audit.put(after, "conventType", e.getConventType() == null ? null : e.getConventType().name());

		d.set("before", before);
		d.set("after", after);

		audit.log(actor, "event.update", d);

		return toDto(e, conventLabels());
	}

	@Transactional
	public void delete(UUID id, UserEntity actor) {
		// Acquired before the entity is read (not just before validateConventChange), for the same
		// reason as in update(): once this blocks on another transaction already holding the lock, `e`
		// would otherwise be loaded from a stale pre-commit snapshot on resume - e.g. still showing
		// deletedAt == null and a protocol-free state for a Convent the other transaction just deleted
		// or protocol-blocked concurrently. Every delete can touch the Convent timeline (deleting a
		// non-Convent event is unaffected either way), so this is unconditional, unlike update().
		acquireConventWriteLock();

		var e = events.findVisibleById(id).orElseThrow(() -> ApiErrors.notFound("Event not found"));

		boolean isAdmin = hasRole(actor, UserRole.ADMIN);
		boolean isSenior = hasRole(actor, UserRole.SENIOR);
		boolean isHousekeeping = hasRole(actor, UserRole.HOUSEKEEPING);

		if (!(isAdmin || isSenior || isHousekeeping)) throw ApiErrors.forbidden("Forbidden");

		if (!isAdmin && !isSenior) {
			if (e.getOwnerType() != EventOwnerType.HOUSEKEEPING) {
				throw ApiErrors.forbidden("HOUSEKEEPING can only delete HOUSEKEEPING events");
			}
			if (!e.getCreatorUserId().equals(actor.getId())) {
				throw ApiErrors.forbidden("HOUSEKEEPING can only delete own events");
			}
			if (e.getConventType() != null) {
				throw ApiErrors.forbidden("Only ADMIN/SENIOR can delete a Convent-flagged event");
			}
		}

		if (e.getConventType() != null) {
			if (protocols.existsByPeriodId(id)) {
				throw StructuredApiError.badRequest(
						"CONVENT_HAS_PROTOCOL",
						"This Convent already has an uploaded Protokoll. Remove the Protokoll before deleting it.",
						StructuredApiError.details("conventId", id, "action", "delete")
				);
			}
			validateConventChange(id, null);
		}

		softDeleteAndCleanup(e);

		var d = audit.obj();
		audit.put(d, "eventId", e.getId());
		audit.put(d, "deletedAt", e.getDeletedAt() == null ? null : e.getDeletedAt().toString());
		audit.log(actor, "event.delete", d);
	}

	/**
	 * Soft-deletes the event and clears/cleans up attendance+fine rows that reference it. Shared by
	 * the single-Event delete flow above and the Convente board's batch delete ({@link ConventBoardService}),
	 * which each do their own role/timeline validation and audit logging around this - this method only
	 * owns the destructive side effects, so they're never duplicated between the two callers.
	 */
	@Transactional
	void softDeleteAndCleanup(EventEntity e) {
		e.setDeletedAt(OffsetDateTime.now());
		events.save(e);

		@SuppressWarnings("unchecked")
		var fineIds = em.createNativeQuery("""
		  select distinct fine_id
		  from attendance
		  where event_id = :eventId
		    and fine_id is not null
		""").setParameter("eventId", e.getId()).getResultList();

		em.createNativeQuery("""
		  update attendance
		  set fine_id = null,
		      deleted_at = coalesce(deleted_at, now())
		  where event_id = :eventId
		""").setParameter("eventId", e.getId()).executeUpdate();

		if (fineIds != null && !fineIds.isEmpty()) {
			for (Object o : fineIds) {
				if (o == null) continue;
				UUID fid = (o instanceof UUID) ? (UUID) o : UUID.fromString(o.toString());
				em.createNativeQuery("delete from fines where id = :id")
						.setParameter("id", fid)
						.executeUpdate();
			}
		}
	}

	/**
	 * Transaction-scoped Postgres advisory lock serializing every structural write to Convent-flagged
	 * events - single-Event create/update/delete here, and the Convente board's batch commit
	 * ({@link ConventBoardService}) - against each other. A row-level lock (PESSIMISTIC_WRITE on the
	 * existing Convent rows, see {@link EventRepository#findAllConventsOrderedVisibleForUpdate()})
	 * cannot protect an *empty* timeline: with zero Convent rows there is nothing to lock, so two
	 * concurrent "create the very first Convent" writes could each validate against the same empty
	 * snapshot and both commit a combined-invalid result. This lock doesn't depend on any row
	 * existing, and auto-releases at commit/rollback - never needs an explicit unlock call.
	 */
	void acquireConventWriteLock() {
		em.createNativeQuery("select pg_advisory_xact_lock(:key)")
				.setParameter("key", CONVENT_WRITE_LOCK_KEY)
				.getSingleResult();
	}

	private static boolean hasRole(UserEntity u, UserRole role) {
		return u.getRoles().stream().anyMatch(r -> r.getRole() == role);
	}

	/** Location is conceptually required, but never worth a hard validation error over. Package-visible
	 * so the Convente board's create path can apply the exact same default. */
	static String normalizeLocation(String raw) {
		String trimmed = raw == null ? "" : raw.trim();
		return trimmed.isEmpty() ? DEFAULT_LOCATION : trimmed;
	}

	private EventDto toDto(EventEntity e, Map<UUID, String> labels) {
		return new EventDto(
				e.getId(),
				e.getCreatorUserId(),
				e.getTitle(),
				e.getLocation(),
				e.getStartsAt(),
				e.isMandatory(),
				e.getEventKind(),
				e.getOwnerType(),
				e.getCreatedAt(),
				e.getConventType(),
				labels.get(e.getId())
		);
	}

	/** id -> "Anconvent" / "1. Convent" / ... / "Abconvent", for every Convent-flagged visible event. */
	private Map<UUID, String> conventLabels() {
		List<ConventDerivation.ConventRef> refs = events.findAllConventsOrderedVisible().stream()
				.map(EventService::toRef)
				.toList();

		Map<UUID, String> out = new HashMap<>();
		for (ConventDerivation.DerivedPeriod p : ConventDerivation.derive(refs)) {
			if (p.id() != null) out.put(p.id(), p.endingConventLabel());
		}
		return out;
	}

	/**
	 * Validates a proposed convent create/move/retype/delete/unmark against the rest of the
	 * timeline: it must not make any previously-consistent Convent inconsistent (pre-existing
	 * legacy inconsistency is never a reason to block unrelated writes, see ConventDerivation),
	 * and it must not silently change the derived range/label of any Convent that already has an
	 * uploaded Protokoll.
	 */
	private void validateConventChange(UUID excludeId, ConventDerivation.ConventRef replacement) {
		List<ConventDerivation.ConventRef> before = currentConventRefs();
		List<ConventDerivation.ConventRef> after = proposedConventRefs(excludeId, replacement);
		UUID targetId = replacement != null ? replacement.id() : null;

		ConventDerivation.validateNoRegression(before, after, targetId);

		Set<UUID> protocolIds = new HashSet<>(protocols.findAllPeriodIds());
		if (excludeId != null) protocolIds.remove(excludeId); // direct edit/delete already blocked earlier with a clearer message
		ConventDerivation.validateProtocolsUnaffected(before, after, protocolIds);
	}

	private List<ConventDerivation.ConventRef> currentConventRefs() {
		return events.findAllConventsOrderedVisible().stream().map(EventService::toRef).toList();
	}

	private List<ConventDerivation.ConventRef> proposedConventRefs(UUID excludeId, ConventDerivation.ConventRef replacement) {
		// Substitutes the target in place (rather than removing then appending at the end) so that,
		// for a same-day pair sharing a date with a pre-existing legacy duplicate, an edit that
		// doesn't touch that duplicate's relative ordering can't spuriously flip which of the two
		// gets flagged by the duplicate-date check (derive() flags whichever sorts second).
		List<ConventDerivation.ConventRef> refs = new ArrayList<>();
		boolean substituted = false;
		for (ConventDerivation.ConventRef ref : currentConventRefs()) {
			if (excludeId != null && excludeId.equals(ref.id())) {
				if (replacement != null) {
					refs.add(replacement);
					substituted = true;
				}
				continue;
			}
			refs.add(ref);
		}
		if (replacement != null && !substituted) refs.add(replacement); // create: target didn't exist before
		refs.sort(Comparator.comparing(ConventDerivation.ConventRef::date));
		return refs;
	}

	private static ConventDerivation.ConventRef toRef(EventEntity e) {
		return new ConventDerivation.ConventRef(e.getId(), toBerlinDate(e.getStartsAt()), e.getConventType());
	}

	private static LocalDate toBerlinDate(OffsetDateTime t) {
		return t.atZoneSameInstant(ZONE_BERLIN).toLocalDate();
	}
}
