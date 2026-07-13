package moe.herz.verhaarmbackend.amt.dto;

import java.util.List;

public record AmtEntryDto(String amtType, String label, boolean autoFromRole, List<AmtHolderDto> holders) {}
