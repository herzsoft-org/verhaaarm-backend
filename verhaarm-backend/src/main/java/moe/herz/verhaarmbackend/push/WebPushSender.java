package moe.herz.verhaarmbackend.push;

import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import nl.martijndwars.webpush.Utils;
import org.apache.http.HttpResponse;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;

@Component
public class WebPushSender {

	private final PushConfigProperties cfg;

	public WebPushSender(PushConfigProperties cfg) {
		this.cfg = cfg;
	}

	public boolean isConfigured() {
		return cfg.getVapid().getPublicKey() != null && !cfg.getVapid().getPublicKey().isBlank()
				&& cfg.getVapid().getPrivateKey() != null && !cfg.getVapid().getPrivateKey().isBlank();
	}

	public void send(String endpoint, String p256dh, String auth, String payloadJson) throws Exception {
		System.out.println("WEBPUSH send called endpoint=" + endpoint);
		System.out.println("WEBPUSH configured=" + isConfigured());
		System.out.println("WEBPUSH subject=" + cfg.getVapid().getSubject());

		if (!isConfigured()) {
			return; // silently skip if not configured
		}

		PushService service = new PushService();

		String pub = cfg.getVapid().getPublicKey();
		String priv = cfg.getVapid().getPrivateKey();

		System.out.println("WEBPUSH public len=" + (pub == null ? "null" : pub.length()));
		System.out.println("WEBPUSH public raw=[" + pub + "]");
		System.out.println("WEBPUSH private len=" + (priv == null ? "null" : priv.length()));
		System.out.println("WEBPUSH private raw=[" + priv + "]");

		try {
			service.setPublicKey(Utils.loadPublicKey(cfg.getVapid().getPublicKey()));
			service.setPrivateKey(Utils.loadPrivateKey(cfg.getVapid().getPrivateKey()));
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("Invalid VAPID keys", e);
		}
		service.setSubject(cfg.getVapid().getSubject());

		Notification n = new Notification(
				endpoint,
				Utils.loadPublicKey(p256dh),
				Base64.getUrlDecoder().decode(auth),
				payloadJson.getBytes(StandardCharsets.UTF_8)
		);


		HttpResponse resp = service.send(n);
		System.out.println("WEBPUSH response status=" + resp.getStatusLine().getStatusCode());
		int sc = resp.getStatusLine().getStatusCode();
		if (sc >= 400) {
			throw new IllegalStateException("WebPush failed: HTTP " + sc);
		}
	}
}
