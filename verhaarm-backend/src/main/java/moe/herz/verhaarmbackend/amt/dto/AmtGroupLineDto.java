package moe.herz.verhaarmbackend.amt.dto;

import java.util.List;

/**
 * One Ehrengericht slot (x / xx / xxx / 1./2. stellvertretender Ehrenrichter).
 * {@code amtType} is the edit target for the whole slot.
 */
public record AmtGroupLineDto(String amtType, String baseLabel, List<AmtSubLineDto> lines) {}
