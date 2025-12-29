package moe.herz.verhaarmbackend.task.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CreateTaskRequest(
		@NotBlank String title,
		String description,
		@NotNull @Size(min = 1) List<UUID> assigneeUserIds
) {}
