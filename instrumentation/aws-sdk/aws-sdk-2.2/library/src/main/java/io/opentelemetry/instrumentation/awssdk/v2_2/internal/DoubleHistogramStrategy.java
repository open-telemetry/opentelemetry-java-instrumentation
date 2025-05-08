package io.opentelemetry.instrumentation.awssdk.v2_2.internal;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.Meter;
import software.amazon.awssdk.metrics.MetricRecord;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DoubleHistogramStrategy implements MetricStrategy {
  private static final Logger logger = Logger.getLogger(DoubleHistogramStrategy.class.getName());
  private final DoubleHistogram histogram;

  public DoubleHistogramStrategy(Meter meter, String metricName, String description) {
    this.histogram = meter.histogramBuilder(metricName)
        .setDescription(description)
        .build();
  }

  @Override
  public void record(MetricRecord<?> metricRecord, Attributes attributes) {
    if (metricRecord.value() instanceof Double) {
      Double value = (Double) metricRecord.value();
      histogram.record(value, attributes);
    } else {
      logger.log(
          Level.WARNING,"Invalid value type for a DoubleHistogram metric: {}", metricRecord.metric().name());
    }
  }
}
