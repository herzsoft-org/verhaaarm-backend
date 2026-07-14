package moe.herz.verhaarmbackend.liveevent;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class LiveEventMaterializationJob {

	private final LiveEventService liveEvents;

	public LiveEventMaterializationJob(LiveEventService liveEvents) {
		this.liveEvents = liveEvents;
	}

	@Scheduled(fixedDelayString = "${verhaarm.live-events.materializeFixedDelayMs:5000}")
	public void materializeRecentlyStartedEvents() {
		liveEvents.materializeRecentlyStartedEvents();
	}
}
