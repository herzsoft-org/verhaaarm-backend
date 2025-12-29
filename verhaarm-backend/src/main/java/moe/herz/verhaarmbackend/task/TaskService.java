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

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TaskService {

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

	@Transactional(readOnly = true)
	public List<TaskDto> listMyTasks(UserEntity actor) {
		if (actor == null) throw ApiErrors.forbidden("Forbidden");
		return tasks.findVisibleForUser(actor.getId()).stream().map(this::toDto).toList();
	}

	@Transactional(readOnly = true)
	public List<TaskDto> listAllTasksAdmin(UserEntity actor) {
		if (actor == null || !users.hasRole(actor.getId(), UserRole.ADMIN)) throw ApiErrors.forbidden("Forbidden");
		return tasks.findAllVisibleWithAssignees().stream().map(this::toDto).toList();
	}

	@Transactional
	public TaskDto create(CreateTaskRequest req, UserEntity actor) {
		if (actor == null) throw ApiErrors.forbidden("Forbidden");

		String title = req.title() == null ? "" : req.title().trim();
		if (title.isBlank()) throw ApiErrors.badRequest("Title required");

		String description = req.description() == null ? "" : req.description().trim();

		List<UUID> assigneeIds = req.assigneeUserIds() == null ? List.of() : req.assigneeUserIds();
		if (assigneeIds.isEmpty()) throw ApiErrors.badRequest("At least one assignee required");

		List<UUID> uniqueAssigneeIds = assigneeIds.stream().filter(Objects::nonNull).distinct().toList();
		if (uniqueAssigneeIds.isEmpty()) throw ApiErrors.badRequest("At least one assignee required");

		Map<UUID, UserEntity> assigneeUsers = loadEnabledUsersOrFail(uniqueAssigneeIds);

		TaskEntity t = new TaskEntity(UUID.randomUUID(), actor.getId(), title, description);

		for (UUID uid : uniqueAssigneeIds) {
			t.getAssignees().add(new TaskAssigneeEntity(t, assigneeUsers.get(uid)));
		}

		tasks.save(t);

		var d = audit.obj();
		audit.put(d, "taskId", t.getId());
		audit.put(d, "creatorUserId", t.getCreatorUserId());
		audit.put(d, "title", t.getTitle());
		audit.put(d, "description", t.getDescription());
		audit.putStringArray(d, "assigneeUserIds", uniqueAssigneeIds.stream().map(UUID::toString).toList());
		audit.log(actor, "task.create", d);

		TaskEntity reloaded = tasks.findVisibleByIdWithAssignees(t.getId())
				.orElseThrow(() -> ApiErrors.notFound("Task not found"));

		// NOTIFICATIONS (one per assignee)
		String nTitle = "Neuer Arbeitsauftrag";
		String nBody = reloaded.getTitle();
		for (UUID assigneeId : uniqueAssigneeIds) {
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

	@Transactional
	public TaskDto update(UUID taskId, UpdateTaskRequest req, UserEntity actor) {
		if (actor == null) throw ApiErrors.forbidden("Forbidden");

		TaskEntity t = tasks.findVisibleByIdWithAssignees(taskId)
				.orElseThrow(() -> ApiErrors.notFound("Task not found"));

		boolean isAdmin = users.hasRole(actor.getId(), UserRole.ADMIN);
		boolean isCreator = t.getCreatorUserId() != null && t.getCreatorUserId().equals(actor.getId());

		if (!isAdmin && !isCreator) throw ApiErrors.forbidden("Forbidden");

		String beforeTitle = t.getTitle();
		String beforeDescription = t.getDescription();
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

		Set<UUID> newlyAddedAssignees = Set.of();

		if (req.assigneeUserIds() != null) {
			List<UUID> incoming = req.assigneeUserIds().stream().filter(Objects::nonNull).distinct().toList();
			if (incoming.isEmpty()) throw ApiErrors.badRequest("At least one assignee required");

			Map<UUID, UserEntity> assigneeUsers = loadEnabledUsersOrFail(incoming);

			Set<UUID> incomingSet = new HashSet<>(incoming);
			Set<UUID> added = new HashSet<>(incomingSet);
			added.removeAll(beforeAssigneeIds);
			newlyAddedAssignees = Set.copyOf(added);

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
		audit.putStringArray(before, "assigneeUserIds", beforeAssigneeIds.stream().map(UUID::toString).sorted().toList());

		var after = audit.obj();
		audit.put(after, "title", t.getTitle());
		audit.put(after, "description", t.getDescription());
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

		boolean isAdmin = users.hasRole(actor.getId(), UserRole.ADMIN);
		boolean isAssignee = tasks.isAssignee(taskId, actor.getId());
		if (!isAdmin && !isAssignee) throw ApiErrors.forbidden("Forbidden");

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

		boolean isAdmin = users.hasRole(actor.getId(), UserRole.ADMIN);
		boolean isAssignee = tasks.isAssignee(taskId, actor.getId());
		if (!isAdmin && !isAssignee) throw ApiErrors.forbidden("Forbidden");

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
		int n = tasks.softDeleteSolvedAssignedToUser(actor.getId(), now);

		var d = audit.obj();
		audit.put(d, "count", n);
		audit.log(actor, "task.deleteSolvedForUser", d);

		return n;
	}

	private Map<UUID, UserEntity> loadEnabledUsersOrFail(List<UUID> ids) {
		List<UserEntity> found = users.findAllById(ids);
		Map<UUID, UserEntity> byId = found.stream().collect(Collectors.toMap(UserEntity::getId, u -> u, (a, b) -> a));

		for (UUID id : ids) {
			if (!byId.containsKey(id)) throw ApiErrors.badRequest("Assignee user not found: " + id);
		}
		for (UserEntity u : found) {
			if (u.isDisabled()) throw ApiErrors.badRequest("Assignee user is disabled: " + u.getId());
		}
		return byId;
	}

	private TaskDto toDto(TaskEntity t) {
		List<UserPickerDto> assignees = t.getAssignees().stream()
				.map(a -> a.getUser())
				.filter(Objects::nonNull)
				.sorted(Comparator.comparing(UserEntity::getUsernameNormalized))
				.map(u -> new UserPickerDto(u.getId(), u.getUsername(), u.getDisplayName()))
				.toList();

		return new TaskDto(
				t.getId(),
				t.getCreatorUserId(),
				t.getTitle(),
				t.getDescription(),
				t.isSolved(),
				t.getSolvedAt(),
				assignees,
				t.getCreatedAt()
		);
	}
}
