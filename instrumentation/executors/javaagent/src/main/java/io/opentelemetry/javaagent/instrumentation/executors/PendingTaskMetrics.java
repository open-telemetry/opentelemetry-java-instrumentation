package io.opentelemetry.javaagent.instrumentation.executors;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.metrics.DoubleHistogram;

// not sure about this
class PendingTaskMetrics {
  private static final DoubleHistogram queueWaitHistogram =
      GlobalOpenTelemetry
          .getMeterProvider()
          .get("io.opentelemetry.executor.queue.wait")
          .histogramBuilder("executor.queue.wait")
          .setUnit("s")
          .setDescription("Time spent waiting in executor queue")
          .build();

  public static void recordTime(Long startTime) {
    if (startTime != null) {
      queueWaitHistogram.record(System.nanoTime() - startTime);
    }

  }

}
