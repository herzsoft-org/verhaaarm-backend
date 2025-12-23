package moe.herz.verhaarmbackend.attendance.dto;

import java.util.List;
import java.util.UUID;

public record GenerateAttendanceFinesResultDto(
		int createdCount,
		List<UUID> fineIds
) {}
