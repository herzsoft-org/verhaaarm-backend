package moe.herz.verhaarmbackend.task.dto;

import jakarta.validation.constraints.Size;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record UpdateTaskRequest(
		String title,
		String description,
		@Size(min = 1) List<UUID> assigneeUserIds,

		OffsetDateTime dueAt,

		Boolean recurringEnabled,
		List<String> recurringWeekdays,
		LocalTime recurringDueTime
) {}
