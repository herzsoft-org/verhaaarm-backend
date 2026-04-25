package moe.herz.verhaarmbackend.auth;

import moe.herz.verhaarmbackend.common.ApiErrors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
public class RefreshTokenService {

	private final RefreshTokenRepository repo;
	private final long ttlSeconds;

	public RefreshTokenService(
			RefreshTokenRepository repo,
			@Value("${verhaarm.security.refresh.ttlSeconds}") long ttlSeconds
	) {
		this.repo = repo;
		this.ttlSeconds = ttlSeconds;
	}

	public record Issued(UUID userId, String refreshToken, UUID refreshId, UUID sessionId, OffsetDateTime expiresAt) {}

	public record Consumed(UUID userId, UUID refreshId, UUID sessionId) {}

	@Transactional
	public Issued issue(UUID userId, UUID sessionId) {
		UUID refreshId = UUID.randomUUID();
		String token = UUID.randomUUID() + "-" + UUID.randomUUID();
		String hash = sha256Hex(token);

		OffsetDateTime expiresAt = OffsetDateTime.now().plusSeconds(ttlSeconds);
		RefreshTokenEntity e = new RefreshTokenEntity(refreshId, userId, sessionId, hash, expiresAt);
		repo.save(e);

		return new Issued(userId, token, refreshId, sessionId, expiresAt);
	}

	@Transactional
	public Consumed consumeForRotation(String presentedToken) {
		String hash = sha256Hex(presentedToken);

		RefreshTokenEntity existing = repo.findByTokenHash(hash)
				.orElseThrow(() -> ApiErrors.unauthorized("Invalid refresh token"));

		if (existing.isRevoked() || existing.isExpired()) {
			throw ApiErrors.unauthorized("Refresh token expired or revoked");
		}

		existing.setRevoked(true);
		repo.save(existing);

		return new Consumed(existing.getUserId(), existing.getId(), existing.getSessionId());
	}

	@Transactional
	public Optional<UUID> revoke(String presentedToken) {
		String hash = sha256Hex(presentedToken);

		return repo.findByTokenHash(hash).map(e -> {
			e.setRevoked(true);
			repo.save(e);
			return e.getSessionId();
		});
	}

	private static String sha256Hex(String s) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			byte[] digest = md.digest(s.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (Exception e) {
			throw ApiErrors.badRequest("Hashing failed");
		}
	}
}