package moe.herz.verhaarmbackend.user.dto;

import java.util.UUID;

public record UserBalanceDto(
		UUID userId,
		long balanceCents,
		String balanceFormatted
) {}