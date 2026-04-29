package moe.herz.verhaarmbackend.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Set;

public record CreateUserRequest(
		@NotBlank String username,
		@NotBlank String displayName,
		@NotBlank String password,
		@NotNull Set<String> roles,
		String memberStatus
) {}