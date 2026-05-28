package moe.herz.verhaarmbackend.liveevent;

import jakarta.validation.Valid;
import moe.herz.verhaarmbackend.liveevent.dto.CreateLiveEventRequest;
import moe.herz.verhaarmbackend.liveevent.dto.LiveEventDto;
import moe.herz.verhaarmbackend.liveevent.dto.LiveEventReactionSummaryDto;
import moe.herz.verhaarmbackend.liveevent.dto.UpdateLiveEventRequest;
import moe.herz.verhaarmbackend.user.UserEntity;
import moe.herz.verhaarmbackend.user.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/live-events")
public class LiveEventController {

	private final LiveEventService liveEvents;
	private final UserRepository users;

	public LiveEventController(LiveEventService liveEvents, UserRepository users) {
		this.liveEvents = liveEvents;
		this.users = users;
	}

	@GetMapping
	public List<LiveEventDto> list(Authentication auth) {
		return liveEvents.listActive(actor(auth));
	}

	@GetMapping("/{id}")
	public LiveEventDto get(@PathVariable UUID id, Authentication auth) {
		return liveEvents.getVisible(id, actor(auth));
	}

	@PostMapping
	public LiveEventDto create(@RequestBody @Valid CreateLiveEventRequest req, Authentication auth) {
		return liveEvents.create(req, actor(auth));
	}

	@PatchMapping("/{id}")
	public LiveEventDto update(@PathVariable UUID id, @RequestBody UpdateLiveEventRequest req, Authentication auth) {
		return liveEvents.update(id, req, actor(auth));
	}

	@DeleteMapping("/{id}")
	public void delete(@PathVariable UUID id, Authentication auth) {
		liveEvents.delete(id, actor(auth));
	}

	@PutMapping("/{id}/reactions/{type}")
	public LiveEventReactionSummaryDto toggleReaction(
			@PathVariable UUID id,
			@PathVariable String type,
			Authentication auth
	) {
		return liveEvents.toggleReaction(id, LiveEventReactionType.fromPath(type), actor(auth));
	}

	private UserEntity actor(Authentication auth) {
		String username = auth.getName();
		return users.findByUsernameWithRoles(username)
				.orElseThrow(() -> moe.herz.verhaarmbackend.common.ApiErrors.unauthorized("Unauthorized"));
	}
}
