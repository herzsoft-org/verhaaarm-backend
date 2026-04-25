package moe.herz.verhaarmbackend.auth.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import moe.herz.verhaarmbackend.session.dto.SessionDeviceInfoRequest;

public record LoginRequest(
		@NotBlank String username,
		@NotBlank String password,

		// Optional for backwards compatibility.
		@Valid SessionDeviceInfoRequest deviceInfo
) {}