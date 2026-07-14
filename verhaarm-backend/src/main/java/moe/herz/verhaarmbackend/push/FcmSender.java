package moe.herz.verhaarmbackend.push;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class FcmSender {

	private final PushConfigProperties cfg;
	private final AtomicBoolean initialized = new AtomicBoolean(false);

	public FcmSender(PushConfigProperties cfg) {
		this.cfg = cfg;
	}

	public boolean isConfigured() {
		return (cfg.getFcm().getServiceAccountPath() != null && !cfg.getFcm().getServiceAccountPath().isBlank())
				|| (cfg.getFcm().getServiceAccountJson() != null && !cfg.getFcm().getServiceAccountJson().isBlank());
	}

	private void initIfNeeded() throws Exception {
		if (initialized.get()) return;
		if (!isConfigured()) return;

		synchronized (this) {
			if (initialized.get()) return;

			GoogleCredentials creds;
			if (cfg.getFcm().getServiceAccountPath() != null && !cfg.getFcm().getServiceAccountPath().isBlank()) {
				try (FileInputStream in = new FileInputStream(cfg.getFcm().getServiceAccountPath())) {
					creds = GoogleCredentials.fromStream(in);
				}
			} else {
				byte[] raw = cfg.getFcm().getServiceAccountJson().getBytes(StandardCharsets.UTF_8);
				try (ByteArrayInputStream in = new ByteArrayInputStream(raw)) {
					creds = GoogleCredentials.fromStream(in);
				}
			}

			FirebaseOptions opts = FirebaseOptions.builder()
					.setCredentials(creds)
					.build();

			if (FirebaseApp.getApps().isEmpty()) FirebaseApp.initializeApp(opts);

			initialized.set(true);
		}
	}

	public void send(String token, Map<String, String> data) throws Exception {
		if (!isConfigured()) return;
		initIfNeeded();

		FirebaseMessaging.getInstance().send(buildMessage(token, data));
	}

	/**
	 * Android push messages are deliberately data-only. The Flutter background
	 * handler is the single owner of notification rendering, which avoids an
	 * FCM-rendered notification being followed by a local duplicate.
	 */
	Message buildMessage(String token, Map<String, String> data) {
		Message.Builder b = Message.builder()
				.setToken(token)
				.setAndroidConfig(AndroidConfig.builder()
						.setPriority(AndroidConfig.Priority.HIGH)
						.build());

		if (data != null) {
			for (var e : data.entrySet()) {
				if (e.getKey() == null || e.getValue() == null) continue;
				b.putData(e.getKey(), e.getValue());
			}
		}

		return b.build();
	}
}
