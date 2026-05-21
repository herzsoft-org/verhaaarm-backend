package moe.herz.verhaarmbackend.settings;

import moe.herz.verhaarmbackend.common.ApiErrors;
import moe.herz.verhaarmbackend.settings.dto.SyncUserSettingsRequest;
import moe.herz.verhaarmbackend.settings.dto.UserSettingsDto;
import moe.herz.verhaarmbackend.user.UserEntity;
import moe.herz.verhaarmbackend.user.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/settings")
public class UserSettingsController {

	private final UserSettingsService settings;
	private final UserRepository users;

	public UserSettingsController(UserSettingsService settings, UserRepository users) {
		this.settings = settings;
		this.users = users;
	}

	@GetMapping("/me")
	public UserSettingsDto get(Authentication auth) {
		UserEntity actor = resolveActor(auth);
		if (actor == null) throw ApiErrors.forbidden("Forbidden");

		return settings.get(actor.getId());
	}

	@PatchMapping("/me")
	public UserSettingsDto sync(
			@RequestBody(required = false) SyncUserSettingsRequest req,
			Authentication auth
	) {
		UserEntity actor = resolveActor(auth);
		if (actor == null) throw ApiErrors.forbidden("Forbidden");

		return settings.sync(actor.getId(), req);
	}

	private UserEntity resolveActor(Authentication auth) {
		if (auth == null || auth.getName() == null || auth.getName().isBlank()) return null;
		return users.findByUsername(auth.getName()).orElse(null);
	}
}