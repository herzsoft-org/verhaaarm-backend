package moe.herz.verhaarmbackend.push.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record RegisterWebPushRequest(
		@NotBlank String endpoint,
		@NotNull Map<String, String> keys,     // expects keys.p256dh and keys.auth
		@NotNull Map<String, Object> raw       // full PushSubscription JSON (store as-is)
) {
}
