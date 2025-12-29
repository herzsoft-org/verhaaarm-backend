package moe.herz.verhaarmbackend.task.dto;

import moe.herz.verhaarmbackend.user.dto.UserPickerDto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record TaskDto(
		UUID id,
		UUID creatorUserId,
		String title,
		String description,
		boolean solved,
		OffsetDateTime solvedAt,
		List<UserPickerDto> assignees,
		OffsetDateTime createdAt
) {}
