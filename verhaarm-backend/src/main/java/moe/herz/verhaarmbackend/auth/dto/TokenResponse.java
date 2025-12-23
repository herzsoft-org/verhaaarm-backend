package moe.herz.verhaarmbackend.auth.dto;

public record TokenResponse(
		String accessToken,
		String refreshToken
) {}
