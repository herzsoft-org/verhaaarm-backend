package moe.herz.verhaarmbackend.fine.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Set;
import java.util.UUID;

public record CreateFineRequest(
		@NotNull UUID periodId,

		// For CATALOG fines
		UUID catalogItemId,

		@NotBlank String reason,

		// For CUSTOM fines (or override if you want) – keep it simple: required always
		@Min(0) int amountCents,

		@NotNull Set<UUID> targetUserIds
) {}
