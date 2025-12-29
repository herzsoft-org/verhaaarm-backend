package moe.herz.verhaarmbackend.push;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "verhaarm.push")
public class PushConfigProperties {

	private boolean enabled = true;

	private final Vapid vapid = new Vapid();
	private final Fcm fcm = new Fcm();

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public Vapid getVapid() {
		return vapid;
	}

	public Fcm getFcm() {
		return fcm;
	}

	public static class Vapid {
		private String publicKey;
		private String privateKey;
		private String subject;

		public String getPublicKey() {
			return publicKey;
		}

		public void setPublicKey(String publicKey) {
			this.publicKey = publicKey;
		}

		public String getPrivateKey() {
			return privateKey;
		}

		public void setPrivateKey(String privateKey) {
			this.privateKey = privateKey;
		}

		public String getSubject() {
			return subject;
		}

		public void setSubject(String subject) {
			this.subject = subject;
		}
	}

	public static class Fcm {
		private String serviceAccountPath;
		private String serviceAccountJson;

		public String getServiceAccountPath() {
			return serviceAccountPath;
		}

		public void setServiceAccountPath(String serviceAccountPath) {
			this.serviceAccountPath = serviceAccountPath;
		}

		public String getServiceAccountJson() {
			return serviceAccountJson;
		}

		public void setServiceAccountJson(String serviceAccountJson) {
			this.serviceAccountJson = serviceAccountJson;
		}
	}
}
