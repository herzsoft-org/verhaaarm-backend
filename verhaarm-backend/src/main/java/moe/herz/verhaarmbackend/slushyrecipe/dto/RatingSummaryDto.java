package moe.herz.verhaarmbackend.slushyrecipe.dto;

public record RatingSummaryDto(
		double average,
		int count,
		Integer myStars,
		String myComment
) {}
