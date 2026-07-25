package moe.herz.verhaarmbackend.event.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record UpdateConventBoardRequest(
		@NotEmpty @Valid List<ConventBoardChangeDto> changes
) {}
