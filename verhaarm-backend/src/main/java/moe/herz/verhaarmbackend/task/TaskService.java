package moe.herz.verhaarmbackend.task;

import moe.herz.verhaarmbackend.audit.AuditLogService;
import moe.herz.verhaarmbackend.common.ApiErrors;
import moe.herz.verhaarmbackend.notification.NotificationService;
import moe.herz.verhaarmbackend.notification.NotificationType;
import moe.herz.verhaarmbackend.task.dto.CreateTaskRequest;
import moe.herz.verhaarmbackend.task.dto.SetTaskSolvedRequest;
import moe.herz.verhaarmbackend.task.dto.TaskDto;
import moe.herz.verhaarmbackend.task.dto.UpdateTaskRequest;
import moe.herz.verhaarmbackend.user.UserEntity;
import moe.herz.verhaarmbackend.user.UserRepository;
import moe.herz.verhaarmbackend.user.UserRole;
import moe.herz.verhaarmbackend.user.dto.UserPickerDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TaskService {

	private static final ZoneId ZONE_BERLIN = ZoneId.of("Europe/Berlin");

	private final TaskRepository tasks;
	private final TaskAssigneeRepository assignees;
	private final UserRepository users;
	private final AuditLogService audit;
	private final NotificationService notifications;

	public TaskService(
			TaskRepository tasks,
			TaskAssigneeRepository assignees,
			UserRepository users,
			AuditLogService audit,
			NotificationService notifications
	) {
		this.tasks = tasks;
		this.assignees = assignees;
		this.users = users;
		this.audit = audit;
		this.notifications = notifications;
	}

	@Transactional
	public List<TaskDto> listMyTasks(UserEntity actor) {
		if (actor == null) throw ApiErrors.forbidden("Forbidden");

		List<TaskEntity> list = tasks.findVisibleForUser(actor.getId());
		// recurring reopening is best-effort; do it in-memory and persist where needed
		reopenRecurringIfNeeded(list);

		return list.stream().map(this::toDto).toList();
	}

	@Transactional
	public List<TaskDto> listAllTasksAdmin(UserEntity actor) {
		if (!canManageAllTasks(actor)) throw ApiErrors.forbidden("Forbidden");

		List<TaskEntity> list = tasks.findAllVisibleWithAssignees();
		reopenRecurringIfNeeded(list);

		return list.stream().map(this::toDto).toList();
	}

	@Transactional
	public TaskDto create(CreateTaskRequest req, UserEntity actor) {
		if (actor == null) throw ApiErrors.forbidden("Forbidden");
		boolean notifyOnlyMe = requireNotifyOnlyMeAllowed(req.notifyOnlyMe(), actor);

		String title = req.title() == null ? "" : req.title().trim();
		if (title.isBlank()) throw ApiErrors.badRequest("Title required");

		String description = req.description() == null ? "" : req.description().trim();

		List<UUID> assigneeIds = req.assigneeUserIds() == null ? List.of() : req.assigneeUserIds();
		if (assigneeIds.isEmpty()) throw ApiErrors.badRequest("At least one assignee required");

		List<UUID> uniqueAssigneeIds = assigneeIds.stream().filter(Objects::nonNull).distinct().toList();
		if (uniqueAssigneeIds.isEmpty()) throw ApiErrors.badRequest("At least one assignee required");

		Map<UUID, UserEntity> assigneeUsers = loadEnabledUsersOrFail(uniqueAssigneeIds);

		boolean recurring = Boolean.TRUE.equals(req.recurringEnabled());

		OffsetDateTime dueAt;
		String recurringDaysStr = "";
		LocalTime recurringDueTime = null;
		LocalDate openFor = null;

		if (!recurring) {
			if (req.dueAt() == null) throw ApiErrors.badRequest("dueAt required");
			dueAt = req.dueAt();
		} else {
			var days = normalizeWeekdays(req.recurringWeekdays());
			if (days.isEmpty()) throw ApiErrors.badRequest("recurringWeekdays required for recurring tasks");
			if (req.recurringDueTime() == null) throw ApiErrors.badRequest("recurringDueTime required for recurring tasks");

			recurringDaysStr = String.join(",", days);
			recurringDueTime = req.recurringDueTime();

			LocalDate today = LocalDate.now(ZONE_BERLIN);
			LocalDate next = nextOccurrenceDate(today, days);
			openFor = next;
			dueAt = toBerlinDueAt(next, recurringDueTime);
		}

		TaskEntity t = new TaskEntity(UUID.randomUUID(), actor.getId(), title, description, dueAt);
		t.setRecurringEnabled(recurring);
		t.setRecurringDays(recurringDaysStr);
		t.setRecurringDueTime(recurringDueTime);
		t.setRecurringOpenFor(openFor);

		for (UUID uid : uniqueAssigneeIds) {
			t.getAssignees().add(new TaskAssigneeEntity(t, assigneeUsers.get(uid)));
		}

		tasks.save(t);

		// keep your existing audit behavior (unchanged)
		var d = audit.obj();
		audit.put(d, "taskId", t.getId());
		audit.put(d, "creatorUserId", t.getCreatorUserId());
		audit.put(d, "title", t.getTitle());
		audit.put(d, "description", t.getDescription());
		audit.put(d, "dueAt", t.getDueAt().toString());
		audit.put(d, "recurringEnabled", t.isRecurringEnabled());
		audit.put(d, "recurringDays", t.getRecurringDays());
		audit.put(d, "recurringDueTime", t.getRecurringDueTime() == null ? null : t.getRecurringDueTime().toString());
		audit.putStringArray(d, "assigneeUserIds", uniqueAssigneeIds.stream().map(UUID::toString).toList());
		audit.log(actor, "task.create", d);

		TaskEntity reloaded = tasks.findVisibleByIdWithAssignees(t.getId())
				.orElseThrow(() -> ApiErrors.notFound("Task not found"));

		// NOTIFICATIONS (one per assignee)
		String nTitle = "Neuer Arbeitsauftrag";
		String nBody = reloaded.getTitle();
		List<UUID> notificationRecipients = notifyOnlyMe ? List.of(actor.getId()) : uniqueAssigneeIds;
		for (UUID assigneeId : notificationRecipients) {
			notifications.createForUser(
					assigneeId,
					NotificationType.TASK_ASSIGNED,
					nTitle,
					nBody,
					Map.of("taskId", reloaded.getId().toString())
			);
		}

		return toDto(reloaded);
	}

	private boolean requireNotifyOnlyMeAllowed(Boolean notifyOnlyMe, UserEntity actor) {
		boolean enabled = Boolean.TRUE.equals(notifyOnlyMe);
		if (enabled && !users.hasRole(actor.getId(), UserRole.ADMIN)) {
			throw ApiErrors.forbidden("notifyOnlyMe requires ADMIN");
		}
		return enabled;
	}

	@Transactional
	public TaskDto update(UUID taskId, UpdateTaskRequest req, UserEntity actor) {
		if (actor == null) throw ApiErrors.forbidden("Forbidden");

		TaskEntity t = tasks.findVisibleByIdWithAssignees(taskId)
				.orElseThrow(() -> ApiErrors.notFound("Task not found"));

		boolean canManageAll = canManageAllTasks(actor);
		boolean isCreator = t.getCreatorUserId() != null && t.getCreatorUserId().equals(actor.getId());
		if (!canManageAll && !isCreator) throw ApiErrors.forbidden("Forbidden");

		String beforeTitle = t.getTitle();
		String beforeDescription = t.getDescription();
		OffsetDateTime beforeDueAt = t.getDueAt();
		boolean beforeRecurring = t.isRecurringEnabled();
		String beforeRecurringDays = t.getRecurringDays();
		LocalTime beforeRecurringDueTime = t.getRecurringDueTime();

		Set<UUID> beforeAssigneeIds = t.getAssignees().stream()
				.map(a -> a.getUser().getId())
				.collect(Collectors.toSet());

		if (req.title() != null) {
			String title = req.title().trim();
			if (title.isBlank()) throw ApiErrors.badRequest("Title required");
			t.setTitle(title);
		}

		if (req.description() != null) {
			t.setDescription(req.description().trim());
		}

		// Recurring config update (if any of the fields is present)
		boolean touchRecurring = req.recurringEnabled() != null || req.recurringWeekdays() != null || req.recurringDueTime() != null;
		if (touchRecurring) {
			boolean recurring = req.recurringEnabled() != null ? Boolean.TRUE.equals(req.recurringEnabled()) : t.isRecurringEnabled();

			if (!recurring) {
				t.setRecurringEnabled(false);
				t.setRecurringDays("");
				t.setRecurringDueTime(null);
				t.setRecurringOpenFor(null);

				// dueAt must then be provided (either existing or request)
				if (req.dueAt() != null) {
					t.setDueAt(req.dueAt());
				} else if (t.getDueAt() == null) {
					throw ApiErrors.badRequest("dueAt required when recurring is disabled");
				}
			} else {
				var days = req.recurringWeekdays() != null ? normalizeWeekdays(req.recurringWeekdays()) : normalizeWeekdays(splitDays(t.getRecurringDays()));
				if (days.isEmpty()) throw ApiErrors.badRequest("recurringWeekdays required for recurring tasks");
				LocalTime dueTime = req.recurringDueTime() != null ? req.recurringDueTime() : t.getRecurringDueTime();
				if (dueTime == null) throw ApiErrors.badRequest("recurringDueTime required for recurring tasks");

				t.setRecurringEnabled(true);
				t.setRecurringDays(String.join(",", days));
				t.setRecurringDueTime(dueTime);

				LocalDate today = LocalDate.now(ZONE_BERLIN);
				LocalDate next = nextOccurrenceDate(today, days);
				t.setRecurringOpenFor(next);
				t.setDueAt(toBerlinDueAt(next, dueTime));

				// recurring tasks should become open on their next occurrence
				t.setSolved(false);
			}
		} else {
			// Normal dueAt edit
			if (req.dueAt() != null) {
				if (t.isRecurringEnabled()) {
					// Recurring tasks derive dueAt from recurrence; prevent manual mismatch
					throw ApiErrors.badRequest("Cannot set dueAt manually for recurring tasks; edit recurring settings instead");
				}
				t.setDueAt(req.dueAt());
			}
		}

		Set<UUID> newlyAddedAssignees = Set.of();

		if (req.assigneeUserIds() != null) {
			List<UUID> incoming = req.assigneeUserIds().stream().filter(Objects::nonNull).distinct().toList();
			if (incoming.isEmpty()) throw ApiErrors.badRequest("At least one assignee required");

			Set<UUID> incomingSet = new HashSet<>(incoming);
			Set<UUID> added = new HashSet<>(incomingSet);
			added.removeAll(beforeAssigneeIds);
			newlyAddedAssignees = Set.copyOf(added);

			// Only newly-added assignees must be enabled; assignees that were already on the
			// task before this edit are kept even if they've since been disabled, so they can
			// still be removed (rather than making the whole task un-editable).
			Map<UUID, UserEntity> assigneeUsers = loadUsersOrFail(incoming);
			for (UUID addedId : newlyAddedAssignees) {
				UserEntity u = assigneeUsers.get(addedId);
				if (u.isDisabled()) throw ApiErrors.badRequest("Assignee user is disabled: " + u.getId());
			}

			t.getAssignees().clear();
			for (UUID uid : incoming) {
				t.getAssignees().add(new TaskAssigneeEntity(t, assigneeUsers.get(uid)));
			}
		}

		tasks.save(t);

		var d = audit.obj();
		audit.put(d, "taskId", t.getId());

		var before = audit.obj();
		audit.put(before, "title", beforeTitle);
		audit.put(before, "description", beforeDescription);
		audit.put(before, "dueAt", beforeDueAt == null ? null : beforeDueAt.toString());
		audit.put(before, "recurringEnabled", beforeRecurring);
		audit.put(before, "recurringDays", beforeRecurringDays);
		audit.put(before, "recurringDueTime", beforeRecurringDueTime == null ? null : beforeRecurringDueTime.toString());
		audit.putStringArray(before, "assigneeUserIds", beforeAssigneeIds.stream().map(UUID::toString).sorted().toList());

		var after = audit.obj();
		audit.put(after, "title", t.getTitle());
		audit.put(after, "description", t.getDescription());
		audit.put(after, "dueAt", t.getDueAt() == null ? null : t.getDueAt().toString());
		audit.put(after, "recurringEnabled", t.isRecurringEnabled());
		audit.put(after, "recurringDays", t.getRecurringDays());
		audit.put(after, "recurringDueTime", t.getRecurringDueTime() == null ? null : t.getRecurringDueTime().toString());
		audit.putStringArray(after, "assigneeUserIds", t.getAssignees().stream()
				.map(a -> a.getUser().getId().toString())
				.sorted()
				.toList());

		d.set("before", before);
		d.set("after", after);

		audit.log(actor, "task.update", d);

		// NOTIFICATIONS: only for newly-added assignees
		if (!newlyAddedAssignees.isEmpty()) {
			String nTitle = "Neuer Arbeitsauftrag";
			String nBody = t.getTitle();
			for (UUID assigneeId : newlyAddedAssignees) {
				notifications.createForUser(
						assigneeId,
						NotificationType.TASK_ASSIGNED,
						nTitle,
						nBody,
						Map.of("taskId", t.getId().toString())
				);
			}
		}

		return toDto(t);
	}

	@Transactional
	public TaskDto setSolved(UUID taskId, SetTaskSolvedRequest req, UserEntity actor) {
		if (actor == null) throw ApiErrors.forbidden("Forbidden");
		if (req == null || req.solved() == null) throw ApiErrors.badRequest("solved required");

		TaskEntity t = tasks.findVisibleByIdWithAssignees(taskId)
				.orElseThrow(() -> ApiErrors.notFound("Task not found"));

		boolean canManageAll = canManageAllTasks(actor);
		boolean isAssignee = tasks.isAssignee(taskId, actor.getId());
		if (!canManageAll && !isAssignee) throw ApiErrors.forbidden("Forbidden");

		boolean beforeSolved = t.isSolved();
		OffsetDateTime beforeSolvedAt = t.getSolvedAt();

		t.setSolved(req.solved());
		tasks.save(t);

		var d = audit.obj();
		audit.put(d, "taskId", t.getId());

		var before = audit.obj();
		audit.put(before, "solved", beforeSolved);
		audit.put(before, "solvedAt", beforeSolvedAt == null ? null : beforeSolvedAt.toString());

		var after = audit.obj();
		audit.put(after, "solved", t.isSolved());
		audit.put(after, "solvedAt", t.getSolvedAt() == null ? null : t.getSolvedAt().toString());

		d.set("before", before);
		d.set("after", after);

		audit.log(actor, "task.setSolved", d);

		return toDto(t);
	}

	@Transactional
	public void delete(UUID taskId, UserEntity actor) {
		if (actor == null) throw ApiErrors.forbidden("Forbidden");

		TaskEntity t = tasks.findVisibleByIdWithAssignees(taskId)
				.orElseThrow(() -> ApiErrors.notFound("Task not found"));

		boolean canManageAll = canManageAllTasks(actor);
		boolean isAssignee = tasks.isAssignee(taskId, actor.getId());
		if (!canManageAll && !isAssignee) throw ApiErrors.forbidden("Forbidden");

		// Weekly recurring tasks: hard delete as requested
		if (t.isRecurringEnabled()) {
			tasks.hardDeleteById(taskId);
			return;
		}

		// Normal tasks: keep existing soft delete
		t.setDeletedAt(OffsetDateTime.now());
		tasks.save(t);

		var d = audit.obj();
		audit.put(d, "taskId", t.getId());
		audit.put(d, "deletedAt", t.getDeletedAt() == null ? null : t.getDeletedAt().toString());
		audit.log(actor, "task.delete", d);
	}

	@Transactional
	public int deleteAllSolvedAssignedToMe(UserEntity actor) {
		if (actor == null) throw ApiErrors.forbidden("Forbidden");
		OffsetDateTime now = OffsetDateTime.now();
		// excludes recurring tasks by query
		int n = tasks.softDeleteSolvedAssignedToUser(actor.getId(), now);

		var d = audit.obj();
		audit.put(d, "count", n);
		audit.log(actor, "task.deleteSolvedForUser", d);

		return n;
	}

	private boolean canManageAllTasks(UserEntity actor) {
		return actor != null && (
				users.hasRole(actor.getId(), UserRole.ADMIN)
						|| users.hasRole(actor.getId(), UserRole.SENIOR)
						|| users.hasRole(actor.getId(), UserRole.HOUSEKEEPING)
		);
	}

	private void reopenRecurringIfNeeded(List<TaskEntity> list) {
		if (list == null || list.isEmpty()) return;

		LocalDate today = LocalDate.now(ZONE_BERLIN);
		for (TaskEntity t : list) {
			if (!t.isRecurringEnabled()) continue;

			var days = normalizeWeekdays(splitDays(t.getRecurringDays()));
			LocalTime dueTime = t.getRecurringDueTime();
			if (days.isEmpty() || dueTime == null) continue; // invalid config: just skip

			// If today is one of the days and we haven't opened for today yet => reopen
			String todayKey = dayKey(today.getDayOfWeek());
			if (!days.contains(todayKey)) continue;

			LocalDate openFor = t.getRecurringOpenFor();
			if (openFor != null && openFor.equals(today)) continue;

			t.setRecurringOpenFor(today);
			t.setDueAt(toBerlinDueAt(today, dueTime));
			t.setSolved(false);
			tasks.save(t);
		}
	}

	private static OffsetDateTime toBerlinDueAt(LocalDate date, LocalTime time) {
		ZonedDateTime zdt = ZonedDateTime.of(date, time, ZONE_BERLIN);
		return zdt.toOffsetDateTime();
	}

	private static LocalDate nextOccurrenceDate(LocalDate fromDate, List<String> days) {
		// We choose the next date >= today that matches one of the weekdays.
		// If today matches, we use today (so it appears immediately).
		for (int i = 0; i < 14; i++) {
			LocalDate d = fromDate.plusDays(i);
			String key = dayKey(d.getDayOfWeek());
			if (days.contains(key)) return d;
		}
		// fallback (should never happen if days non-empty)
		return fromDate.plusDays(7);
	}

	private static List<String> normalizeWeekdays(List<String> raw) {
		if (raw == null) return List.of();
		List<String> out = new ArrayList<>();
		for (String s : raw) {
			if (s == null) continue;
			String v = s.trim().toUpperCase(Locale.ROOT);
			if (v.isBlank()) continue;
			// accept MON..SUN only
			if (Set.of("MON","TUE","WED","THU","FRI","SAT","SUN").contains(v)) out.add(v);
		}
		return out.stream().distinct().toList();
	}

	private static List<String> splitDays(String csv) {
		if (csv == null || csv.isBlank()) return List.of();
		return Arrays.stream(csv.split(","))
				.map(String::trim)
				.filter(s -> !s.isBlank())
				.toList();
	}

	private static String dayKey(DayOfWeek dow) {
		return switch (dow) {
			case MONDAY -> "MON";
			case TUESDAY -> "TUE";
			case WEDNESDAY -> "WED";
			case THURSDAY -> "THU";
			case FRIDAY -> "FRI";
			case SATURDAY -> "SAT";
			case SUNDAY -> "SUN";
		};
	}

	private Map<UUID, UserEntity> loadEnabledUsersOrFail(List<UUID> ids) {
		Map<UUID, UserEntity> byId = loadUsersOrFail(ids);
		for (UserEntity u : byId.values()) {
			if (u.isDisabled()) throw ApiErrors.badRequest("Assignee user is disabled: " + u.getId());
		}
		return byId;
	}

	private Map<UUID, UserEntity> loadUsersOrFail(List<UUID> ids) {
		List<UserEntity> found = users.findAllById(ids);
		Map<UUID, UserEntity> byId = found.stream().collect(Collectors.toMap(UserEntity::getId, u -> u, (a, b) -> a));

		for (UUID id : ids) {
			if (!byId.containsKey(id)) throw ApiErrors.badRequest("Assignee user not found: " + id);
		}
		return byId;
	}

	private TaskDto toDto(TaskEntity t) {
		List<UserPickerDto> assignees = t.getAssignees().stream()
				.map(TaskAssigneeEntity::getUser)
				.filter(Objects::nonNull)
				.sorted(Comparator.comparing(UserEntity::getUsernameNormalized))
				.map(u -> new UserPickerDto(
						u.getId(),
						u.getUsername(),
						u.getDisplayName(),
						u.getMemberStatus() == null ? "BURSCH" : u.getMemberStatus().name(),
						u.getMemberStatus() == null || u.getMemberStatus().isAktivitas(),
						u.isDisabled()
				))
				.toList();

		var days = normalizeWeekdays(splitDays(t.getRecurringDays()));

		return new TaskDto(
				t.getId(),
				t.getCreatorUserId(),
				t.getTitle(),
				t.getDescription(),
				t.isSolved(),
				t.getSolvedAt(),
				t.getDueAt(),
				t.isRecurringEnabled(),
				days,
				t.getRecurringDueTime(),
				assignees,
				t.getCreatedAt()
		);
	}
}
