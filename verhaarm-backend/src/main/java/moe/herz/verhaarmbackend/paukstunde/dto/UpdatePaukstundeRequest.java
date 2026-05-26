package moe.herz.verhaarmbackend.paukstunde.dto;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

public record UpdatePaukstundeRequest(
		LocalDate date,
		Integer hours,
		Set<UUID> participantUserIds
) {}
