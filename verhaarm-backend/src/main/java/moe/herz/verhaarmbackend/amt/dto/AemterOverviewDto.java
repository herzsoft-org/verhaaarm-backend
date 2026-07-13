package moe.herz.verhaarmbackend.amt.dto;

import java.util.List;

public record AemterOverviewDto(List<AmtGroupLineDto> ehrengericht, List<AmtEntryDto> other) {}
