package moe.herz.verhaarmbackend.liveevent.dto;

public record UpdateLiveEventRequest(
		String title,
		String place,
		String description
) {}
