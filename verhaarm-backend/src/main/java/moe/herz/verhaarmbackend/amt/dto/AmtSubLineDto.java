package moe.herz.verhaarmbackend.amt.dto;

import java.util.List;

/**
 * One rendered row within an Ehrengericht slot. Usually a slot has exactly one
 * sub-line; multiple only occur if the slot has several holders with different
 * combined-Amt sets (data-correction edge case).
 */
public record AmtSubLineDto(String displayTitle, List<AmtHolderDto> holders) {}
