package moe.herz.verhaarmbackend.push.dto;

import jakarta.validation.constraints.NotBlank;

public record RegisterFcmRequest(
		@NotBlank String token
) {
}
