package moe.herz.verhaarmbackend.ferienvertreter.dto;

import moe.herz.verhaarmbackend.user.dto.UserPickerDto;

import java.time.LocalDate;
import java.util.UUID;

public record FerienvertreterDto(
		UUID id,
		UserPickerDto person,
		LocalDate fromDate,
		LocalDate untilDate
) {}
