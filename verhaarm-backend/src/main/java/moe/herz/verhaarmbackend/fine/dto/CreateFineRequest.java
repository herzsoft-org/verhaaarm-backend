package moe.herz.verhaarmbackend.fine.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

public record CreateFineRequest(
		@NotNull LocalDate fineDate,
		UUID catalogItemId,
		String reason,
		Integer amountCents,
		@NotNull Set<UUID> targetUserIds,
		Boolean notifyOnlyMe
) {}
