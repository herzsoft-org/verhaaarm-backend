package moe.herz.verhaarmbackend.auth;

import moe.herz.verhaarmbackend.auth.dto.LoginRequest;
import moe.herz.verhaarmbackend.auth.dto.RefreshRequest;
import moe.herz.verhaarmbackend.auth.dto.TokenResponse;
import moe.herz.verhaarmbackend.common.ApiErrors;
import moe.herz.verhaarmbackend.user.UserEntity;
import moe.herz.verhaarmbackend.user.UserRepository;
import moe.herz.verhaarmbackend.user.UserRole;
import moe.herz.verhaarmbackend.user.UserRoleEntity;

import jakarta.validation.Valid;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/auth")
public class AuthController {

	private final AuthenticationManager authManager;
	private final UserRepository users;
	private final JwtService jwtService;
	private final RefreshTokenService refreshTokens;

	public AuthController(AuthenticationManager authManager, UserRepository users, JwtService jwtService, RefreshTokenService refreshTokens) {
		this.authManager = authManager;
		this.users = users;
		this.jwtService = jwtService;
		this.refreshTokens = refreshTokens;
	}

	@PostMapping("/login")
	public TokenResponse login(@Valid @RequestBody LoginRequest req) {
		try {
			authManager.authenticate(new UsernamePasswordAuthenticationToken(req.username(), req.password()));
		} catch (Exception e) {
			throw ApiErrors.unauthorized("Invalid credentials");
		}

		// IMPORTANT: load roles eagerly (fixes LazyInitializationException)
		UserEntity u = users.findByUsernameWithRoles(req.username())
				.orElseThrow(() -> ApiErrors.unauthorized("Invalid credentials"));

		if (u.isDisabled()) throw ApiErrors.unauthorized("User disabled");

		Set<UserRole> roles = u.getRoles().stream()
				.map(UserRoleEntity::getRole)
				.collect(Collectors.toSet());

		String access = jwtService.issueAccessToken(u.getId(), u.getUsername(), roles);
		var issued = refreshTokens.issue(u.getId());

		return new TokenResponse(access, issued.refreshToken());
	}

	@PostMapping("/refresh")
	public TokenResponse refresh(@Valid @RequestBody RefreshRequest req) {
		var rotated = refreshTokens.rotate(req.refreshToken());

		// IMPORTANT: load roles eagerly (refresh also issues token with roles)
		UserEntity u = users.findByIdWithRoles(rotated.userId())
				.orElseThrow(() -> ApiErrors.unauthorized("User not found"));

		if (u.isDisabled()) throw ApiErrors.unauthorized("User disabled");

		Set<UserRole> roles = u.getRoles().stream()
				.map(UserRoleEntity::getRole)
				.collect(Collectors.toSet());

		String access = jwtService.issueAccessToken(u.getId(), u.getUsername(), roles);

		return new TokenResponse(access, rotated.refreshToken());
	}

	@PostMapping("/logout")
	public void logout(@Valid @RequestBody RefreshRequest req) {
		refreshTokens.revoke(req.refreshToken());
	}
}
