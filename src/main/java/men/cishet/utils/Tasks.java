package men.cishet.utils;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class Tasks {
	private static final ScheduledExecutorService executor = Executors.newScheduledThreadPool(8);

	public static ScheduledFuture<?> runTaskLater(Runnable runnable, int delayMS) {
		return executor.schedule(runnable, delayMS, TimeUnit.MILLISECONDS);
	}

	public static ScheduledFuture<?> runAsync(Runnable runnable) {
		return executor.schedule(runnable, 0, TimeUnit.MILLISECONDS);
	}

	public static ScheduledFuture<?> repeatAsync(Runnable runnable, long initialDelayMS, long intervalMS) {
		return executor.scheduleWithFixedDelay(runnable, initialDelayMS, intervalMS, TimeUnit.MILLISECONDS);
	}
}
