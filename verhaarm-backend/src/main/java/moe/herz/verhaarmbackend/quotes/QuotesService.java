package moe.herz.verhaarmbackend.quotes;

import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class QuotesService {

	private static final URI QUOTES_URI = URI.create("https://herz.moe/verhaarm/zitate.json");
	private static final Duration TTL = Duration.ofMinutes(2);

	private final HttpClient http = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(5))
			.followRedirects(HttpClient.Redirect.NORMAL)
			.build();

	private final ReentrantLock lock = new ReentrantLock();

	private volatile String cachedJson = null;
	private volatile long cachedAtMs = 0L;

	public String getQuotesJson() {
		final long now = System.currentTimeMillis();
		final String current = cachedJson;

		if (current != null && (now - cachedAtMs) < TTL.toMillis()) {
			return current;
		}

		lock.lock();
		try {
			final long now2 = System.currentTimeMillis();
			final String current2 = cachedJson;
			if (current2 != null && (now2 - cachedAtMs) < TTL.toMillis()) {
				return current2;
			}

			final HttpRequest req = HttpRequest.newBuilder()
					.uri(QUOTES_URI)
					.timeout(Duration.ofSeconds(8))
					.GET()
					.header("Accept", "application/json")
					.build();

			final HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());

			if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
				final String body = resp.body();
				if (body != null && !body.isBlank()) {
					cachedJson = body;
					cachedAtMs = now2;
					return body;
				}
			}

			// If fetch failed but we have older cache, serve it.
			return cachedJson;
		} catch (Exception ignored) {
			return cachedJson;
		} finally {
			lock.unlock();
		}
	}
}
