package moe.herz.verhaarmbackend.liveevent;

import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class LiveEventMaterializationJobTest {

	@Test
	void scheduledRunMaterializesRecentlyStartedEvents() {
		LiveEventService liveEvents = mock(LiveEventService.class);
		LiveEventMaterializationJob job = new LiveEventMaterializationJob(liveEvents);

		job.materializeRecentlyStartedEvents();

		verify(liveEvents).materializeRecentlyStartedEvents();
	}
}
