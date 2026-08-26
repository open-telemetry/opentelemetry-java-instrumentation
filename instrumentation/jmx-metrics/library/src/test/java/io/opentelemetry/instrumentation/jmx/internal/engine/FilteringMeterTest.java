package io.opentelemetry.instrumentation.jmx.internal.engine;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.metrics.BatchCallback;
import io.opentelemetry.api.metrics.DoubleGaugeBuilder;
import io.opentelemetry.api.metrics.DoubleHistogramBuilder;
import io.opentelemetry.api.metrics.LongCounterBuilder;
import io.opentelemetry.api.metrics.LongUpDownCounterBuilder;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableMeasurement;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FilteringMeterTest {

  private static final IncludeExclude INCLUDE_EXCLUDE = IncludeExclude.builder()
      .setExcluded("excluded*").build();

  private static void dummyRunnable() {}

  private FilteringMeter meter;

  @BeforeEach
  void before() {
    InMemoryMetricReader metricReader = InMemoryMetricReader.create();
    SdkMeterProvider meterProvider =
        SdkMeterProvider.builder().registerMetricReader(metricReader).build();
    Meter sdkMeter = meterProvider.get("test");
    meter = new FilteringMeter(sdkMeter, INCLUDE_EXCLUDE);
  }

  @Test
  void counter() {
    LongCounterBuilder noopBuilder = meter.counterBuilder("excluded");
    checkNoop(noopBuilder);
    checkNoop(noopBuilder.buildObserver());

    checkBatchCallback(noopBuilder.buildObserver());

    LongCounterBuilder builder = meter.counterBuilder("included");
    checkSdk(builder);
    checkSdk(builder.buildObserver());

    checkBatchCallback(builder.buildObserver());
    checkBatchCallback(builder.buildObserver(), noopBuilder.buildObserver(), meter.counterBuilder("included2").buildObserver());
  }

  @Test
  void upDownCounter() {
    LongUpDownCounterBuilder noopBuilder = meter.upDownCounterBuilder("excluded");
    checkNoop(noopBuilder);
    checkNoop(noopBuilder.buildObserver());

    checkBatchCallback(noopBuilder.buildObserver());

    LongUpDownCounterBuilder builder = meter.upDownCounterBuilder("included");
    checkSdk(builder);
    checkSdk(builder.buildObserver());

    checkBatchCallback(builder.buildObserver());
    checkBatchCallback(builder.buildObserver(), noopBuilder.buildObserver(), meter.upDownCounterBuilder("included2").buildObserver());
  }

  @Test
  void histogram() {
    DoubleHistogramBuilder noopBuilder = meter.histogramBuilder("excluded");
    checkNoop(noopBuilder);

    DoubleHistogramBuilder builder = meter.histogramBuilder("included");
    checkSdk(builder);
  }

  @Test
  void gauge() {
    DoubleGaugeBuilder noopBuilder = meter.gaugeBuilder("excluded");
    checkNoop(noopBuilder);
    checkNoop(noopBuilder.buildObserver());

    checkBatchCallback(noopBuilder.buildObserver());

    DoubleGaugeBuilder builder = meter.gaugeBuilder("included");
    checkSdk(builder);
    checkSdk(builder.buildObserver());

    checkBatchCallback(builder.buildObserver());
    checkBatchCallback(builder.buildObserver(), noopBuilder.buildObserver(), meter.gaugeBuilder("included2").buildObserver());
  }


  private void checkBatchCallback(ObservableMeasurement first, ObservableMeasurement... rest) {
    try(BatchCallback callback = meter.batchCallback(FilteringMeterTest::dummyRunnable, first,
        rest)) {
    }
  }

  private static void checkNoop(Object o) {
    String className = o.getClass().getName();
    assertThat(className).startsWith("io.opentelemetry.api.").contains("Noop");
  }

  private static void checkSdk(Object o) {
    String className = o.getClass().getName();
    assertThat(className).startsWith("io.opentelemetry.sdk.").doesNotContain("Noop");
  }

}
