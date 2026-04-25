package moe.herz.verhaarmbackend.auth;

import moe.herz.verhaarmbackend.auth.dto.LoginRequest;
import moe.herz.verhaarmbackend.auth.dto.RefreshRequest;
import moe.herz.verhaarmbackend.auth.dto.TokenResponse;
import moe.herz.verhaarmbackend.common.ApiErrors;
import moe.herz.verhaarmbackend.session.UserSessionEntity;
import moe.herz.verhaarmbackend.session.UserSessionService;
import moe.herz.verhaarmbackend.user.UserEntity;
import moe.herz.verhaarmbackend.user.UserRepository;
import moe.herz.verhaarmbackend.user.UserRole;
import moe.herz.verhaarmbackend.user.UserRoleEntity;

import jakarta.validation.Valid;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/auth")
public class AuthController {

	private final AuthenticationManager authManager;
	private final UserRepository users;
	private final JwtService jwtService;
	private final RefreshTokenService refreshTokens;
	private final UserSessionService sessions;

	public AuthController(
			AuthenticationManager authManager,
			UserRepository users,
			JwtService jwtService,
			RefreshTokenService refreshTokens,
			UserSessionService sessions
	) {
		this.authManager = authManager;
		this.users = users;
		this.jwtService = jwtService;
		this.refreshTokens = refreshTokens;
		this.sessions = sessions;
	}

	@PostMapping("/login")
	public TokenResponse login(@Valid @RequestBody LoginRequest req) {
		try {
			authManager.authenticate(new UsernamePasswordAuthenticationToken(req.username(), req.password()));
		} catch (Exception e) {
			throw ApiErrors.unauthorized("Invalid credentials");
		}

		UserEntity u = users.findByUsernameWithRoles(req.username())
				.orElseThrow(() -> ApiErrors.unauthorized("Invalid credentials"));

		if (u.isDisabled()) throw ApiErrors.unauthorized("User disabled");

		Set<UserRole> roles = u.getRoles().stream()
				.map(UserRoleEntity::getRole)
				.collect(Collectors.toSet());

		UserSessionEntity session = sessions.createSession(u.getId(), req.deviceInfo());

		String access = jwtService.issueAccessToken(u.getId(), u.getUsername(), roles, session.getId());
		var issued = refreshTokens.issue(u.getId(), session.getId());
		sessions.updateExpiresAt(session.getId(), issued.expiresAt());

		return new TokenResponse(access, issued.refreshToken(), session.getId());
	}

	@PostMapping("/refresh")
	public TokenResponse refresh(@Valid @RequestBody RefreshRequest req) {
		var consumed = refreshTokens.consumeForRotation(req.refreshToken());

		UserEntity u = users.findByIdWithRoles(consumed.userId())
				.orElseThrow(() -> ApiErrors.unauthorized("User not found"));

		if (u.isDisabled()) throw ApiErrors.unauthorized("User disabled");

		UUID sessionId = consumed.sessionId();

		// Backwards compatibility:
		// Old refresh tokens from before V35 have no session_id.
		// On first refresh after the backend update, attach them to a new legacy session.
		if (sessionId == null) {
			sessionId = sessions.createSession(u.getId(), req.deviceInfo()).getId();
		} else {
			sessions.touch(sessionId, u.getId(), req.deviceInfo());
		}

		Set<UserRole> roles = u.getRoles().stream()
				.map(UserRoleEntity::getRole)
				.collect(Collectors.toSet());

		String access = jwtService.issueAccessToken(u.getId(), u.getUsername(), roles, sessionId);
		var issued = refreshTokens.issue(u.getId(), sessionId);
		sessions.updateExpiresAt(sessionId, issued.expiresAt());

		return new TokenResponse(access, issued.refreshToken(), sessionId);
	}

	@PostMapping("/logout")
	public void logout(@Valid @RequestBody RefreshRequest req) {
		refreshTokens.revoke(req.refreshToken())
				.ifPresent(sessions::revokeSessionById);
	}
}