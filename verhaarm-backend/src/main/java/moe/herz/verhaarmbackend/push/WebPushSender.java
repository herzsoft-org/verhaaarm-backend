package moe.herz.verhaarmbackend.push;

import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import nl.martijndwars.webpush.Utils;
import org.apache.http.HttpResponse;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Security;
import java.util.Base64;

import org.apache.http.HttpEntity;
import org.apache.http.util.EntityUtils;

@Component
public class WebPushSender {

	private final PushConfigProperties cfg;

	static {
		// Ensure BC is available and preferred for EC key handling used by webpush-java.
		if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
			Security.insertProviderAt(new BouncyCastleProvider(), 1);
		}
	}

	public WebPushSender(PushConfigProperties cfg) {
		this.cfg = cfg;
	}

	public boolean isConfigured() {
		return cfg.getVapid().getPublicKey() != null && !cfg.getVapid().getPublicKey().isBlank()
				&& cfg.getVapid().getPrivateKey() != null && !cfg.getVapid().getPrivateKey().isBlank();
	}

	public void send(String endpoint, String p256dh, String auth, String payloadJson) throws Exception {
		if (!isConfigured()) {
			return;
		}

		String pub = cfg.getVapid().getPublicKey().trim();
		String priv = cfg.getVapid().getPrivateKey().trim();

		System.out.println("WEBPUSH send called endpoint=" + endpoint);
		System.out.println("WEBPUSH configured=" + isConfigured());
		System.out.println("WEBPUSH subject=" + cfg.getVapid().getSubject());
		System.out.println("WEBPUSH public len=" + pub.length());
		System.out.println("WEBPUSH public raw=[" + pub + "]");
		System.out.println("WEBPUSH private len=" + priv.length());
		System.out.println("WEBPUSH private raw=[" + priv + "]");
		System.out.println("WEBPUSH BC provider=" + Security.getProvider(BouncyCastleProvider.PROVIDER_NAME));

		PushService service = new PushService();
		try {
			service.setPublicKey(Utils.loadPublicKey(pub));
			service.setPrivateKey(Utils.loadPrivateKey(priv));
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

		ttpResponse resp = service.send(n);
		int sc = resp.getStatusLine().getStatusCode();

		String responseBody = null;
		HttpEntity entity = resp.getEntity();
		if (entity != null) {
			responseBody = EntityUtils.toString(entity, StandardCharsets.UTF_8);
		}

		System.out.println("WEBPUSH response status=" + sc);
		System.out.println("WEBPUSH response body=" + responseBody);

		if (sc >= 400) {
			throw new IllegalStateException("WebPush failed: HTTP " + sc + " body=" + responseBody);
		}
	}
}