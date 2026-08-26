package io.opentelemetry.instrumentation.jmx.internal.engine;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.metrics.LongCounterBuilder;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FilteringMeterTest {

  private static final IncludeExclude INCLUDE_EXCLUDE = IncludeExclude.builder()
      .setExcluded("excluded*").build();
  private static final Runnable DUMMY_RUNNABLE = () -> {
  };

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

    meter.batchCallback(DUMMY_RUNNABLE, noopBuilder.buildObserver());

    LongCounterBuilder builder = meter.counterBuilder("included");
    checkSdk(builder);
    checkSdk(builder.buildObserver());

    meter.batchCallback(DUMMY_RUNNABLE, builder.buildObserver());
    meter.batchCallback(DUMMY_RUNNABLE, builder.buildObserver(), noopBuilder.buildObserver());
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
