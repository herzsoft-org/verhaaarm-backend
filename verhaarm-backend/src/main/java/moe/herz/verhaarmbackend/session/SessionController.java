package moe.herz.verhaarmbackend.session;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import moe.herz.verhaarmbackend.auth.JwtService;
import moe.herz.verhaarmbackend.common.ApiErrors;
import moe.herz.verhaarmbackend.session.dto.SessionDeviceInfoRequest;
import moe.herz.verhaarmbackend.session.dto.SessionStatsResponse;
import moe.herz.verhaarmbackend.session.dto.UserSessionDto;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/sessions")
public class SessionController {

	private final UserSessionService sessions;
	private final JwtService jwtService;

	public SessionController(UserSessionService sessions, JwtService jwtService) {
		this.sessions = sessions;
		this.jwtService = jwtService;
	}

	@GetMapping("/me")
	public List<UserSessionDto> ownSessions(HttpServletRequest request) {
		CurrentSession current = current(request);
		return sessions.listOwn(current.userId(), current.sessionId());
	}

	@PostMapping("/me/touch")
	public ResponseEntity<Void> touch(
			@RequestBody(required = false) SessionDeviceInfoRequest info,
			HttpServletRequest request
	) {
		CurrentSession current = current(request);

		if (current.sessionId() != null) {
			sessions.touch(current.sessionId(), current.userId(), info);
		}

		return ResponseEntity.noContent().build();
	}

	@DeleteMapping("/me/{sessionId}")
	public ResponseEntity<Void> revokeOwn(
			@PathVariable UUID sessionId,
			HttpServletRequest request
	) {
		CurrentSession current = current(request);
		sessions.revokeOwn(current.userId(), sessionId, current.sessionId());
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/admin/stats")
	@PreAuthorize("hasRole('ADMIN')")
	public SessionStatsResponse stats() {
		return sessions.stats();
	}

	private CurrentSession current(HttpServletRequest request) {
		String header = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (header == null || !header.startsWith("Bearer ")) {
			throw ApiErrors.unauthorized("Missing token");
		}

		String token = header.substring("Bearer ".length()).trim();

		Claims claims;
		try {
			claims = jwtService.parse(token);
		} catch (Exception e) {
			throw ApiErrors.unauthorized("Invalid token");
		}

		String uidRaw = claims.get("uid", String.class);
		if (uidRaw == null || uidRaw.isBlank()) {
			throw ApiErrors.unauthorized("Invalid token");
		}

		UUID userId = UUID.fromString(uidRaw);

		String sidRaw = claims.get("sid", String.class);
		UUID sessionId = null;
		if (sidRaw != null && !sidRaw.isBlank()) {
			sessionId = UUID.fromString(sidRaw);
		}

		return new CurrentSession(userId, sessionId);
	}

	private record CurrentSession(UUID userId, UUID sessionId) {}
}