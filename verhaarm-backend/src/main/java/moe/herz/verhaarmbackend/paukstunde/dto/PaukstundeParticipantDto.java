package moe.herz.verhaarmbackend.paukstunde.dto;

import java.util.UUID;

public record PaukstundeParticipantDto(
		UUID id,
		String username,
		String displayName,
		String memberStatus
) {}
