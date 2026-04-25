package moe.herz.verhaarmbackend.user.dto;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

public record UserDto(
		UUID id,
		String username,
		String displayName,
		boolean disabled,
		Set<String> roles,
		OffsetDateTime lastOnlineAt
) {}