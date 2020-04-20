package com.datadog.profiling.exceptions;

import datadog.trace.api.Config;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import jdk.jfr.EventType;

final class ExceptionSampler {
  /*
   * Fixed 0.5 second sampling window.
   * Logic in StreamingSampler relies on sampling window being small compared to (in our case) recording duration:
   * sampler may overshoot on one given window but should average to samplesPerWindow in the long run.
   */
  private static final Duration SAMPLING_WINDOW = Duration.of(500, ChronoUnit.MILLIS);

  private final StreamingSampler sampler;
  private final EventType exceptionSampleType;

  ExceptionSampler(final Config config) {
    this(SAMPLING_WINDOW, getSamplesPerWindow(config), samplingWindowsPerRecording(config));
  }

  ExceptionSampler(final Duration windowDuration, final int samplesPerWindow, final int lookback) {
    sampler = new StreamingSampler(windowDuration, samplesPerWindow, lookback);
    exceptionSampleType = EventType.getEventType(ExceptionSampleEvent.class);
  }

  private static int samplingWindowsPerRecording(final Config config) {
    /*
     * Java8 doesn't have dividedBy#Duration so we have to implement poor man's version.
     * None of these durations should be big enough to warrant dealing with bigints.
     * We also do not care about nanoseconds here.
     */
    return (int)
        Math.min(
            Duration.of(config.getProfilingUploadPeriod(), ChronoUnit.SECONDS).toMillis()
                / SAMPLING_WINDOW.toMillis(),
            Integer.MAX_VALUE);
  }

  private static int getSamplesPerWindow(final Config config) {
    return config.getProfilingExceptionSampleLimit() / samplingWindowsPerRecording(config);
  }

  boolean sample() {
    return exceptionSampleType.isEnabled() && sampler.sample();
  }
}
