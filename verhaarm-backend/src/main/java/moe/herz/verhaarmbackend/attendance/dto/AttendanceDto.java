package moe.herz.verhaarmbackend.attendance.dto;

import moe.herz.verhaarmbackend.attendance.AttendanceStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AttendanceDto(
		UUID id,
		UUID eventId,
		UUID periodId,
		UUID userId,
		AttendanceStatus status,
		Integer lateMinutes,
		UUID fineId,
		OffsetDateTime createdAt
) {}
