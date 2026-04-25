package moe.herz.verhaarmbackend.session;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class UserSessionCleanupJob {

	private final UserSessionService sessions;
	private final long revokedDeleteAfterMinutes;

	public UserSessionCleanupJob(
			UserSessionService sessions,
			@Value("${verhaarm.sessions.revokedDeleteAfterMinutes:10}") long revokedDeleteAfterMinutes
	) {
		this.sessions = sessions;
		this.revokedDeleteAfterMinutes = revokedDeleteAfterMinutes;
	}

	@Scheduled(fixedDelayString = "${verhaarm.sessions.cleanupFixedDelayMs:60000}")
	public void cleanupRevokedSessions() {
		sessions.deleteRevokedOlderThan(Duration.ofMinutes(revokedDeleteAfterMinutes));
	}
}