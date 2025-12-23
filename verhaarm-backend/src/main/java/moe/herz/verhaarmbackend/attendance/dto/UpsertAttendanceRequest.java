package moe.herz.verhaarmbackend.attendance.dto;

import jakarta.validation.constraints.NotNull;
import moe.herz.verhaarmbackend.attendance.AttendanceStatus;

import java.util.UUID;

public record UpsertAttendanceRequest(
		@NotNull UUID userId,
		@NotNull AttendanceStatus status,
		Integer lateMinutes
) {}
