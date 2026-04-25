package moe.herz.verhaarmbackend.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import moe.herz.verhaarmbackend.user.UserRole;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

@Service
public class JwtService {

	private final SecretKey key;
	private final String issuer;
	private final long accessTtlSeconds;

	public JwtService(
			@Value("${verhaarm.security.jwt.secret}") String secret,
			@Value("${verhaarm.security.jwt.issuer}") String issuer,
			@Value("${verhaarm.security.jwt.accessTtlSeconds}") long accessTtlSeconds
	) {
		if (secret == null || secret.isBlank()) {
			throw new IllegalStateException("JWT secret missing (set VERHAARM_JWT_SECRET)");
		}
		// JJWT HS256 expects a sufficiently long key; enforce a minimum.
		if (secret.length() < 32) {
			throw new IllegalStateException("JWT secret too short (min 32 chars)");
		}

		this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
		this.issuer = issuer;
		this.accessTtlSeconds = accessTtlSeconds;
	}

	public String issueAccessToken(UUID userId, String username, Set<UserRole> roles, UUID sessionId) {
		Instant now = Instant.now();
		Instant exp = now.plusSeconds(accessTtlSeconds);

		Map<String, Object> claims = new HashMap<>();
		claims.put("uid", userId.toString());
		claims.put("roles", roles.stream().map(Enum::name).toList());

		if (sessionId != null) {
			claims.put("sid", sessionId.toString());
		}

		return Jwts.builder()
				.issuer(issuer)
				.subject(username)
				.issuedAt(Date.from(now))
				.expiration(Date.from(exp))
				.claims(claims)
				.signWith(key, Jwts.SIG.HS256)
				.compact();
	}

	public Claims parse(String jwt) {
		return Jwts.parser()
				.verifyWith(key)
				.requireIssuer(issuer)
				.build()
				.parseSignedClaims(jwt)
				.getPayload();
	}
}
