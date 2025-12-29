package moe.herz.verhaarmbackend.task.dto;

import jakarta.validation.constraints.NotNull;

public record SetTaskSolvedRequest(
		@NotNull Boolean solved
) {}
