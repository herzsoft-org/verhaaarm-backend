package moe.herz.verhaarmbackend.user.dto;

import java.util.UUID;

public record UserPickerDto(
		UUID id,
		String username,
		String displayName,
		String memberStatus,
		boolean aktivitas
) {}