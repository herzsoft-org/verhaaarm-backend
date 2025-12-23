package moe.herz.verhaarmbackend.user.dto;

import jakarta.validation.constraints.NotNull;

import java.util.Set;

public record UpdateUserRequest(
		String displayName,
		Boolean disabled,
		@NotNull Set<String> roles
) {}
