package moe.herz.verhaarmbackend.user.dto;

import java.util.Set;

public record UpdateUserRequest(
		String displayName,
		Boolean disabled,
		Set<String> roles,
		String memberStatus
) {}