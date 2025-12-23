package moe.herz.verhaarmbackend.liveevent.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateLiveEventRequest(
		@NotBlank String title,
		@NotBlank String place,
		@NotBlank String description
) {}
