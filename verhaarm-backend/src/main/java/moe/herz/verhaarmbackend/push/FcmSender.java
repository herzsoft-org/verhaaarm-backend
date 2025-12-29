package moe.herz.verhaarmbackend.push;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
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
		if (!initialized.compareAndSet(false, true)) return;

		if (!isConfigured()) {
			return;
		}

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

		// Avoid double init
		if (FirebaseApp.getApps().isEmpty()) {
			FirebaseApp.initializeApp(opts);
		}
	}

	public void send(String token, String title, String body, String dataJson) throws Exception {
		if (!isConfigured()) return;
		initIfNeeded();

		// Minimal: notification title/body + one data field (optional)
		Message.Builder b = Message.builder()
				.setToken(token)
				.setNotification(Notification.builder().setTitle(title).setBody(body).build());

		if (dataJson != null && !dataJson.isBlank()) {
			b.putData("data", dataJson);
		}

		FirebaseMessaging.getInstance().send(b.build());
	}
}
