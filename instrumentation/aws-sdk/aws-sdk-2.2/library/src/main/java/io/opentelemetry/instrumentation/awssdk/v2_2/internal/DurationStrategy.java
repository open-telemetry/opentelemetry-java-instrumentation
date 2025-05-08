package io.opentelemetry.instrumentation.awssdk.v2_2.internal;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongHistogram;
import io.opentelemetry.api.metrics.Meter;
import software.amazon.awssdk.metrics.MetricRecord;

import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DurationStrategy implements MetricStrategy {
  private static final Logger logger = Logger.getLogger(DurationStrategy.class.getName());
  private final LongHistogram histogram;

  public DurationStrategy(Meter meter, String metricName, String description) {
    this.histogram = meter.histogramBuilder(metricName)
        .setDescription(description)
        .setUnit("ns")
        .ofLongs()
        .build();
  }

  @Override
  public void record(MetricRecord<?> metricRecord, Attributes attributes) {
    if (metricRecord.value() instanceof Duration) {
      Duration duration = (Duration) metricRecord.value();
      histogram.record(duration.toNanos(), attributes);
    } else {
      logger.log(Level.WARNING, "Invalid value type for duration metric: {}", metricRecord.metric().name());
    }
  }
}
