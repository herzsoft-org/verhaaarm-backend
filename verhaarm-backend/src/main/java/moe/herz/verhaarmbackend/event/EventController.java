package moe.herz.verhaarmbackend.event;

import jakarta.validation.Valid;
import moe.herz.verhaarmbackend.event.dto.CreateEventRequest;
import moe.herz.verhaarmbackend.event.dto.EventDto;
import moe.herz.verhaarmbackend.event.dto.UpdateEventRequest;
import moe.herz.verhaarmbackend.user.UserEntity;
import moe.herz.verhaarmbackend.user.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/events")
public class EventController {

	private final EventService events;
	private final UserRepository users;

	public EventController(EventService events, UserRepository users) {
		this.events = events;
		this.users = users;
	}

	@GetMapping
	public List<EventDto> list(Authentication auth) {
		UserEntity actor = actor(auth);
		return events.listVisible(actor);
	}

	@GetMapping("/{id}")
	public EventDto get(@PathVariable UUID id, Authentication auth) {
		UserEntity actor = actor(auth);
		return events.getVisible(id, actor);
	}

	@PostMapping
	public EventDto create(@RequestBody @Valid CreateEventRequest req, Authentication auth) {
		UserEntity actor = actor(auth);
		return events.create(req, actor);
	}

	@PatchMapping("/{id}")
	public EventDto update(@PathVariable UUID id, @RequestBody UpdateEventRequest req, Authentication auth) {
		UserEntity actor = actor(auth);
		return events.update(id, req, actor);
	}

	@DeleteMapping("/{id}")
	public void delete(@PathVariable UUID id, Authentication auth) {
		UserEntity actor = actor(auth);
		events.delete(id, actor);
	}

	private UserEntity actor(Authentication auth) {
		String username = auth.getName();
		return users.findByUsernameWithRoles(username)
				.orElseThrow(() -> moe.herz.verhaarmbackend.common.ApiErrors.unauthorized("Unauthorized"));
	}
}
