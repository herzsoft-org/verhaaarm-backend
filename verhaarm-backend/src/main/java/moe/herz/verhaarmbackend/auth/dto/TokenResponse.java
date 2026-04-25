package moe.herz.verhaarmbackend.auth.dto;

import java.util.UUID;

public record TokenResponse(
		String accessToken,
		String refreshToken,
		UUID sessionId
) {}