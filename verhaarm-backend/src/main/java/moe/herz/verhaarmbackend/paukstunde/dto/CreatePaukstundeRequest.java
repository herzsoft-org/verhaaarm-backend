package moe.herz.verhaarmbackend.paukstunde.dto;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

public record CreatePaukstundeRequest(
		LocalDate date,
		Integer hours,
		Set<UUID> participantUserIds
) {}
