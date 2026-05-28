package moe.herz.verhaarmbackend.task.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record CreateTaskRequest(
		@NotBlank String title,
		String description,
		@NotNull @Size(min = 1) List<UUID> assigneeUserIds,

		// For normal tasks: required (backend enforces)
		OffsetDateTime dueAt,

		// For weekly recurring tasks:
		Boolean recurringEnabled,
		List<String> recurringWeekdays, // e.g. ["MON","WED","FRI"]
		LocalTime recurringDueTime,      // due time (Berlin local)
		Boolean notifyOnlyMe
) {}
