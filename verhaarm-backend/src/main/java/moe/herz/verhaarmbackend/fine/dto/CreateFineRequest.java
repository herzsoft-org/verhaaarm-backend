package moe.herz.verhaarmbackend.fine.dto;

import jakarta.validation.constraints.NotNull;

import java.util.Set;
import java.util.UUID;

public record CreateFineRequest(
		@NotNull UUID periodId,
		UUID catalogItemId,
		String reason,
		Integer amountCents,
		@NotNull Set<UUID> targetUserIds
) {}
