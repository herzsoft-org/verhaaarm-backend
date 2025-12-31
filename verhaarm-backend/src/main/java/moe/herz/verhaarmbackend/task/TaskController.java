package moe.herz.verhaarmbackend.task;

import jakarta.validation.Valid;
import moe.herz.verhaarmbackend.common.ApiErrors;
import moe.herz.verhaarmbackend.task.dto.CreateTaskRequest;
import moe.herz.verhaarmbackend.task.dto.SetTaskSolvedRequest;
import moe.herz.verhaarmbackend.task.dto.TaskDto;
import moe.herz.verhaarmbackend.task.dto.UpdateTaskRequest;
import moe.herz.verhaarmbackend.user.UserEntity;
import moe.herz.verhaarmbackend.user.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping
public class TaskController {

	private final TaskService tasks;
	private final UserRepository userRepo;

	public TaskController(TaskService tasks, UserRepository userRepo) {
		this.tasks = tasks;
		this.userRepo = userRepo;
	}

	@GetMapping("/tasks")
	@PreAuthorize("isAuthenticated()")
	public List<TaskDto> myTasks(Authentication auth) {
		UserEntity actor = resolveActor(auth);
		if (actor == null) throw ApiErrors.forbidden("Forbidden");
		return tasks.listMyTasks(actor);
	}

	@GetMapping("/admin/tasks")
	@PreAuthorize("hasRole('ADMIN')")
	public List<TaskDto> allTasks(Authentication auth) {
		UserEntity actor = resolveActor(auth);
		return tasks.listAllTasksAdmin(actor);
	}

	@PostMapping("/tasks")
	@PreAuthorize("isAuthenticated()")
	public TaskDto create(@Valid @RequestBody CreateTaskRequest req, Authentication auth) {
		UserEntity actor = resolveActor(auth);
		return tasks.create(req, actor);
	}

	@PatchMapping("/tasks/{taskId}")
	@PreAuthorize("isAuthenticated()")
	public TaskDto update(@PathVariable UUID taskId, @Valid @RequestBody UpdateTaskRequest req, Authentication auth) {
		UserEntity actor = resolveActor(auth);
		return tasks.update(taskId, req, actor);
	}

	@PostMapping("/tasks/{taskId}/solved")
	@PreAuthorize("isAuthenticated()")
	public TaskDto setSolved(@PathVariable UUID taskId, @Valid @RequestBody SetTaskSolvedRequest req, Authentication auth) {
		UserEntity actor = resolveActor(auth);
		return tasks.setSolved(taskId, req, actor);
	}

	@DeleteMapping("/tasks/{taskId}")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<Void> delete(@PathVariable UUID taskId, Authentication auth) {
		UserEntity actor = resolveActor(auth);
		tasks.delete(taskId, actor);
		return ResponseEntity.noContent().build();
	}

	@DeleteMapping("/tasks/solved")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<Void> deleteAllSolvedAssignedToMe(Authentication auth) {
		UserEntity actor = resolveActor(auth);
		if (actor == null) throw ApiErrors.forbidden("Forbidden");
		tasks.deleteAllSolvedAssignedToMe(actor);
		return ResponseEntity.noContent().build();
	}

	private UserEntity resolveActor(Authentication auth) {
		if (auth == null || auth.getName() == null || auth.getName().isBlank()) return null;
		return userRepo.findByUsername(auth.getName()).orElse(null);
	}
}
