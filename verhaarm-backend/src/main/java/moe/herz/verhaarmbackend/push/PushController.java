package moe.herz.verhaarmbackend.push;

import jakarta.validation.Valid;
import moe.herz.verhaarmbackend.common.ApiErrors;
import moe.herz.verhaarmbackend.push.dto.RegisterFcmRequest;
import moe.herz.verhaarmbackend.push.dto.RegisterWebPushRequest;
import moe.herz.verhaarmbackend.user.UserEntity;
import moe.herz.verhaarmbackend.user.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/push")
public class PushController {

	private final PushService push;
	private final UserRepository userRepo;

	public PushController(PushService push, UserRepository userRepo) {
		this.push = push;
		this.userRepo = userRepo;
	}

	@PostMapping("/register/webpush")
	public ResponseEntity<Void> registerWebPush(
			@Valid @RequestBody RegisterWebPushRequest req,
			Authentication auth,
			@RequestHeader(value = "User-Agent", required = false) String userAgent
	) {
		UserEntity actor = resolveActor(auth);
		push.registerWebPush(req, actor, userAgent);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/register/fcm")
	public ResponseEntity<Void> registerFcm(
			@Valid @RequestBody RegisterFcmRequest req,
			Authentication auth,
			@RequestHeader(value = "User-Agent", required = false) String userAgent
	) {
		UserEntity actor = resolveActor(auth);
		push.registerFcm(req, actor, userAgent);
		return ResponseEntity.noContent().build();
	}

	private UserEntity resolveActor(Authentication auth) {
		if (auth == null || auth.getName() == null || auth.getName().isBlank()) return null;
		UserEntity actor = userRepo.findByUsername(auth.getName()).orElse(null);
		if (actor == null) throw ApiErrors.forbidden("Forbidden");
		return actor;
	}
}
