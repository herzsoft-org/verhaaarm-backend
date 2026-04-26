package moe.herz.verhaarmbackend.session;

import moe.herz.verhaarmbackend.auth.RefreshTokenRepository;
import moe.herz.verhaarmbackend.common.ApiErrors;
import moe.herz.verhaarmbackend.session.dto.SessionDeviceInfoRequest;
import moe.herz.verhaarmbackend.session.dto.SessionStatsResponse;
import moe.herz.verhaarmbackend.session.dto.SessionStatsRowDto;
import moe.herz.verhaarmbackend.session.dto.UserSessionDto;
import moe.herz.verhaarmbackend.user.UserRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.time.Duration;

@Service
public class UserSessionService {

	private final UserSessionRepository sessions;
	private final RefreshTokenRepository refreshTokens;
	private final UserRepository users;

	public UserSessionService(
			UserSessionRepository sessions,
			RefreshTokenRepository refreshTokens,
			UserRepository users
	) {
		this.sessions = sessions;
		this.refreshTokens = refreshTokens;
		this.users = users;
	}

	@Transactional
	public UserSessionEntity createSession(UUID userId, SessionDeviceInfoRequest info) {
		UserSessionEntity s = new UserSessionEntity(
				UUID.randomUUID(),
				userId,
				AppType.fromNullable(info == null ? null : info.appType())
		);

		applyDeviceInfo(s, info);
		s.setLastActiveAt(OffsetDateTime.now());

		UserSessionEntity saved = sessions.save(s);
		users.updateLastOnlineAt(userId, saved.getLastActiveAt());

		return saved;
	}

	@Transactional
	public UserSessionEntity touch(UUID sessionId, UUID userId, SessionDeviceInfoRequest info) {
		UserSessionEntity s = sessions.findById(sessionId)
				.orElseThrow(() -> ApiErrors.unauthorized("Session not found"));

		if (!s.getUserId().equals(userId)) {
			throw ApiErrors.forbidden("Forbidden");
		}

		if (s.isRevoked()) {
			throw ApiErrors.unauthorized("Session revoked");
		}

		applyDeviceInfo(s, info);
		s.setLastActiveAt(OffsetDateTime.now());

		UserSessionEntity saved = sessions.save(s);
		users.updateLastOnlineAt(userId, saved.getLastActiveAt());

		return saved;
	}

	@Transactional
	public int deleteRevokedOlderThan(Duration age) {
		OffsetDateTime cutoff = OffsetDateTime.now().minus(age);
		return sessions.deleteRevokedBefore(cutoff);
	}

	@Transactional
	public void updateExpiresAt(UUID sessionId, OffsetDateTime expiresAt) {
		sessions.findById(sessionId).ifPresent(s -> {
			s.setExpiresAt(expiresAt);
			sessions.save(s);
		});
	}

	@Transactional(readOnly = true)
	public List<UserSessionDto> listOwn(UUID userId, UUID currentSessionId) {
		return sessions.findAllForUser(userId)
				.stream()
				.map(s -> toDto(s, currentSessionId))
				.toList();
	}

	@Transactional
	public void revokeOwn(UUID userId, UUID sessionId, UUID currentSessionId) {
		if (currentSessionId != null && currentSessionId.equals(sessionId)) {
			throw ApiErrors.badRequest("Cannot revoke current session here; use logout");
		}

		UserSessionEntity s = sessions.findById(sessionId)
				.orElseThrow(() -> ApiErrors.notFound("Session not found"));

		if (!s.getUserId().equals(userId)) {
			throw ApiErrors.forbidden("Forbidden");
		}

		revokeSession(s);
	}

	@Transactional
	public void revokeSessionById(UUID sessionId) {
		sessions.findById(sessionId).ifPresent(this::revokeSession);
	}

	@Transactional(readOnly = true)
	public SessionStatsResponse stats() {
		OffsetDateTime now = OffsetDateTime.now();

		return new SessionStatsResponse(
				groupStats(sessions.findActiveSince(now.minusDays(7), now)),
				groupStats(sessions.findActiveSince(now.minusMonths(1), now)),
				groupStats(sessions.findActiveSince(now.minusYears(1), now))
		);
	}

	private void revokeSession(UserSessionEntity s) {
		if (s.getRevokedAt() == null) {
			s.setRevokedAt(OffsetDateTime.now());
			sessions.save(s);
		}

		refreshTokens.revokeAllForSession(s.getId());
	}

	private List<SessionStatsRowDto> groupStats(List<UserSessionEntity> input) {
		Map<String, Long> grouped = input.stream()
				.collect(Collectors.groupingBy(s -> {
					String appType = s.getAppType() == null ? "UNKNOWN" : s.getAppType().name();
					String detail = sessionStatsDetail(s, appType);

					return appType + "\u0000" + detail;
				}, Collectors.counting()));

		return grouped.entrySet()
				.stream()
				.map(e -> {
					String[] parts = e.getKey().split("\u0000", -1);
					return new SessionStatsRowDto(parts[0], parts[1], e.getValue());
				})
				.sorted(
						Comparator.comparing(SessionStatsRowDto::appType)
								.thenComparing(SessionStatsRowDto::detail)
				)
				.toList();
	}

	private String sessionStatsDetail(UserSessionEntity s, String appType) {
		if ("WEB".equals(appType)) {
			String browser = normalizeBrowserName(safeBlank(s.getBrowserName(), "Unbekannter Browser"));
			String os = normalizeOsName(safeBlank(s.getOsName(), "Unbekanntes System"));

			return browser + " · " + os;
		}

		if ("ANDROID".equals(appType)) {
			String osName = normalizeOsName(safeBlank(s.getOsName(), "Android"));
			String osVersion = safeBlank(s.getOsVersion(), "");

			if (osVersion.isBlank()) {
				return osName;
			}

			if ("Android".equalsIgnoreCase(osName)) {
				return "Android " + osVersion;
			}

			return osName + " " + osVersion;
		}

		return "Unbekannt";
	}

	private static String normalizeBrowserName(String value) {
		if (value == null || value.isBlank()) return "Unbekannter Browser";

		return switch (value.trim().toUpperCase()) {
			case "CHROME" -> "Chrome";
			case "SAFARI" -> "Safari";
			case "FIREFOX" -> "Firefox";
			case "EDGE" -> "Edge";
			case "OPERA" -> "Opera";
			case "SAMSUNG", "SAMSUNG INTERNET" -> "Samsung Internet";
			case "UNKNOWN" -> "Unbekannter Browser";
			default -> value.trim();
		};
	}

	private static String normalizeOsName(String value) {
		if (value == null || value.isBlank()) return "Unbekanntes System";

		return switch (value.trim().toUpperCase()) {
			case "ANDROID" -> "Android";
			case "IOS" -> "iOS";
			case "IPADOS" -> "iPadOS";
			case "MACOS", "MAC OS", "MAC OS X" -> "macOS";
			case "WINDOWS" -> "Windows";
			case "LINUX" -> "Linux";
			case "UNKNOWN" -> "Unbekanntes System";
			default -> value.trim();
		};
	}

	private UserSessionDto toDto(UserSessionEntity s, UUID currentSessionId) {
		return new UserSessionDto(
				s.getId(),
				s.getUserId(),
				s.getAppType() == null ? "UNKNOWN" : s.getAppType().name(),
				s.getDeviceName(),
				s.getDeviceModel(),
				s.getOsName(),
				s.getOsVersion(),
				s.getBrowserName(),
				s.getBrowserVersion(),
				s.getUserAgent(),
				s.getCreatedAt(),
				s.getLastActiveAt(),
				s.getExpiresAt(),
				s.getRevokedAt(),
				currentSessionId != null && currentSessionId.equals(s.getId())
		);
	}

	private void applyDeviceInfo(UserSessionEntity s, SessionDeviceInfoRequest info) {
		if (info == null) return;

		s.setAppType(AppType.fromNullable(info.appType()));

		if (hasText(info.deviceName())) s.setDeviceName(limit(info.deviceName(), 255));
		if (hasText(info.deviceModel())) s.setDeviceModel(limit(info.deviceModel(), 255));
		if (hasText(info.osName())) s.setOsName(limit(info.osName(), 120));
		if (hasText(info.osVersion())) s.setOsVersion(limit(info.osVersion(), 120));
		if (hasText(info.browserName())) s.setBrowserName(limit(info.browserName(), 120));
		if (hasText(info.browserVersion())) s.setBrowserVersion(limit(info.browserVersion(), 120));
		if (hasText(info.userAgent())) s.setUserAgent(limit(info.userAgent(), 1000));
	}

	private static boolean hasText(String s) {
		return s != null && !s.isBlank();
	}

	private static String limit(String s, int max) {
		if (s == null) return null;
		String trimmed = s.trim();
		return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
	}

	private static String safeBlank(String s, String fallback) {
		return s == null || s.isBlank() ? fallback : s;
	}
}