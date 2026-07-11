package moe.herz.verhaarmbackend.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import moe.herz.verhaarmbackend.audit.AuditLogRepository;
import moe.herz.verhaarmbackend.audit.AuditLogService;
import moe.herz.verhaarmbackend.notification.NotificationService;
import moe.herz.verhaarmbackend.notification.NotificationType;
import moe.herz.verhaarmbackend.task.dto.CreateTaskRequest;
import moe.herz.verhaarmbackend.task.dto.UpdateTaskRequest;
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
class TaskServiceTest {

	@Mock
	private TaskRepository tasks;
	@Mock
	private TaskAssigneeRepository assignees;
	@Mock
	private UserRepository users;
	@Mock
	private AuditLogRepository auditRepo;
	@Mock
	private NotificationService notifications;

	private TaskService service;

	@BeforeEach
	void setUp() {
		service = new TaskService(
				tasks,
				assignees,
				users,
				new AuditLogService(auditRepo, new ObjectMapper()),
				notifications
		);
	}

	@Test
	void createWithDefaultRequestNotifiesAssignees() {
		UserEntity actor = user(UserRole.MEMBER);
		UserEntity assignee = user(UserRole.MEMBER);
		AtomicReference<TaskEntity> saved = stubCreateReload();
		when(users.findAllById(List.of(assignee.getId()))).thenReturn(List.of(assignee));

		service.create(request(List.of(assignee.getId()), null), actor);

		verify(notifications).createForUser(
				eq(assignee.getId()),
				eq(NotificationType.TASK_ASSIGNED),
				eq("Neuer Arbeitsauftrag"),
				eq("Testauftrag"),
				anyMap()
		);
		assertEquals(1, saved.get().getAssignees().size());
	}

	@Test
	void createWithNotifyOnlyMeFalseNotifiesAssignees() {
		UserEntity actor = user(UserRole.MEMBER);
		UserEntity assignee = user(UserRole.MEMBER);
		stubCreateReload();
		when(users.findAllById(List.of(assignee.getId()))).thenReturn(List.of(assignee));

		service.create(request(List.of(assignee.getId()), false), actor);

		verify(notifications).createForUser(eq(assignee.getId()), eq(NotificationType.TASK_ASSIGNED), any(), any(), anyMap());
		verify(notifications, never()).createForUser(eq(actor.getId()), any(), any(), any(), anyMap());
	}

	@Test
	void adminCanCreateTaskAndNotifyOnlySelfWithoutChangingAssigneesOrPayload() {
		UserEntity actor = user(UserRole.ADMIN);
		UserEntity assignee = user(UserRole.MEMBER);
		AtomicReference<TaskEntity> saved = stubCreateReload();
		when(users.hasRole(actor.getId(), UserRole.ADMIN)).thenReturn(true);
		when(users.findAllById(List.of(assignee.getId()))).thenReturn(List.of(assignee));

		service.create(request(List.of(assignee.getId()), true), actor);

		@SuppressWarnings("unchecked")
		ArgumentCaptor<Map<String, Object>> dataCaptor = ArgumentCaptor.forClass(Map.class);
		verify(notifications).createForUser(
				eq(actor.getId()),
				eq(NotificationType.TASK_ASSIGNED),
				eq("Neuer Arbeitsauftrag"),
				eq("Testauftrag"),
				dataCaptor.capture()
		);
		verify(notifications, never()).createForUser(eq(assignee.getId()), any(), any(), any(), anyMap());
		assertEquals(assignee.getId(), saved.get().getAssignees().iterator().next().getUser().getId());
		assertEquals(saved.get().getId().toString(), dataCaptor.getValue().get("taskId"));
	}

	@Test
	void nonAdminCannotUseTaskNotifyOnlyMe() {
		UserEntity actor = user(UserRole.MEMBER);

		ResponseStatusException ex = assertThrows(
				ResponseStatusException.class,
				() -> service.create(request(List.of(UUID.randomUUID()), true), actor)
		);

		assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
		assertEquals("notifyOnlyMe requires ADMIN", ex.getReason());
		verify(tasks, never()).save(any());
		verifyNoInteractions(notifications);
	}

	@Test
	void updateRetainsExistingDisabledAssigneeWithoutError() {
		UserEntity creator = user(UserRole.MEMBER);
		UserEntity disabledAssignee = new UserEntity(UUID.randomUUID(), "disabled-user", "Disabled User", "hash", true);
		TaskEntity task = new TaskEntity(UUID.randomUUID(), creator.getId(), "Titel", "Beschreibung", OffsetDateTime.now());
		task.getAssignees().add(new TaskAssigneeEntity(task, disabledAssignee));

		when(tasks.findVisibleByIdWithAssignees(task.getId())).thenReturn(Optional.of(task));
		when(users.findAllById(List.of(disabledAssignee.getId()))).thenReturn(List.of(disabledAssignee));

		assertDoesNotThrow(() -> service.update(
				task.getId(),
				new UpdateTaskRequest("Neuer Titel", null, List.of(disabledAssignee.getId()), null, null, null, null),
				creator
		));

		verify(tasks).save(task);
		assertEquals(1, task.getAssignees().size());
	}

	@Test
	void updateRejectsNewlyAddedDisabledAssignee() {
		UserEntity creator = user(UserRole.MEMBER);
		UserEntity enabledAssignee = user(UserRole.MEMBER);
		UserEntity disabledAssignee = new UserEntity(UUID.randomUUID(), "disabled-user", "Disabled User", "hash", true);
		TaskEntity task = new TaskEntity(UUID.randomUUID(), creator.getId(), "Titel", "Beschreibung", OffsetDateTime.now());
		task.getAssignees().add(new TaskAssigneeEntity(task, enabledAssignee));

		when(tasks.findVisibleByIdWithAssignees(task.getId())).thenReturn(Optional.of(task));
		when(users.findAllById(List.of(enabledAssignee.getId(), disabledAssignee.getId())))
				.thenReturn(List.of(enabledAssignee, disabledAssignee));

		ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.update(
				task.getId(),
				new UpdateTaskRequest(null, null, List.of(enabledAssignee.getId(), disabledAssignee.getId()), null, null, null, null),
				creator
		));

		assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
		verify(tasks, never()).save(any());
	}

	@Test
	void updateAllowsRemovingDisabledAssignee() {
		UserEntity creator = user(UserRole.MEMBER);
		UserEntity enabledAssignee = user(UserRole.MEMBER);
		UserEntity disabledAssignee = new UserEntity(UUID.randomUUID(), "disabled-user", "Disabled User", "hash", true);
		TaskEntity task = new TaskEntity(UUID.randomUUID(), creator.getId(), "Titel", "Beschreibung", OffsetDateTime.now());
		task.getAssignees().add(new TaskAssigneeEntity(task, enabledAssignee));
		task.getAssignees().add(new TaskAssigneeEntity(task, disabledAssignee));

		when(tasks.findVisibleByIdWithAssignees(task.getId())).thenReturn(Optional.of(task));
		when(users.findAllById(List.of(enabledAssignee.getId()))).thenReturn(List.of(enabledAssignee));

		assertDoesNotThrow(() -> service.update(
				task.getId(),
				new UpdateTaskRequest(null, null, List.of(enabledAssignee.getId()), null, null, null, null),
				creator
		));

		assertEquals(1, task.getAssignees().size());
		assertEquals(enabledAssignee.getId(), task.getAssignees().iterator().next().getUser().getId());
	}

	private AtomicReference<TaskEntity> stubCreateReload() {
		AtomicReference<TaskEntity> saved = new AtomicReference<>();
		when(tasks.save(any(TaskEntity.class))).thenAnswer(invocation -> {
			TaskEntity task = invocation.getArgument(0);
			saved.set(task);
			return task;
		});
		when(tasks.findVisibleByIdWithAssignees(any(UUID.class))).thenAnswer(invocation -> Optional.of(saved.get()));
		return saved;
	}

	private static CreateTaskRequest request(List<UUID> assigneeUserIds, Boolean notifyOnlyMe) {
		return new CreateTaskRequest(
				"Testauftrag",
				"Beschreibung",
				assigneeUserIds,
				OffsetDateTime.parse("2026-05-28T12:00:00+02:00"),
				false,
				null,
				null,
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
