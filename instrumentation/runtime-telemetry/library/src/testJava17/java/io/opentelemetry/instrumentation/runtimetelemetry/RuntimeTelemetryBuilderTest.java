/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimetelemetry;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.runtimetelemetry.internal.Experimental;
import io.opentelemetry.instrumentation.runtimetelemetry.internal.Internal;
import io.opentelemetry.instrumentation.runtimetelemetry.internal.JfrConfig;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import jdk.jfr.FlightRecorder;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class RuntimeTelemetryBuilderTest {

  @RegisterExtension final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  @BeforeAll
  static void setup() {
    Assumptions.assumeTrue(FlightRecorder.isAvailable(), "JFR not available");
  }

  @Test
  void build_DefaultNoJfr() {
    RuntimeTelemetry runtimeTelemetry = RuntimeTelemetry.builder(OpenTelemetry.noop()).build();
    cleanup.deferCleanup(runtimeTelemetry);

    assertThat(runtimeTelemetry.getJfrTelemetry()).isNull();
  }

  @Test
  void setJfrMetricsSelectsExactMetric() {
    RuntimeTelemetryBuilder builder = RuntimeTelemetry.builder(OpenTelemetry.noop());
    Experimental.setJfrMetrics(builder, singletonList("jvm.cpu.longlock"));
    RuntimeTelemetry runtimeTelemetry = builder.build();
    cleanup.deferCleanup(runtimeTelemetry);

    JfrConfig.JfrRuntimeMetrics jfrRuntimeMetrics =
        (JfrConfig.JfrRuntimeMetrics) runtimeTelemetry.getJfrTelemetry();
    assertThat(jfrRuntimeMetrics.getMetricNames()).containsExactly("jvm.cpu.longlock");
    assertThat(jfrRuntimeMetrics.getRecordedEventHandlers())
        .singleElement()
        .satisfies(
            handler -> {
              assertThat(handler.getEventName()).isEqualTo("jdk.JavaMonitorWait");
              assertThat(handler.getMetricNames()).containsExactly("jvm.cpu.longlock");
            });
  }

  @Test
  void setJfrMetricsSelectsMetricWithinSharedHandler() {
    RuntimeTelemetryBuilder builder = RuntimeTelemetry.builder(OpenTelemetry.noop());
    Experimental.setJfrMetrics(builder, singletonList("jvm.cpu.recent_utilization"));
    RuntimeTelemetry runtimeTelemetry = builder.build();
    cleanup.deferCleanup(runtimeTelemetry);

    JfrConfig.JfrRuntimeMetrics jfrRuntimeMetrics =
        (JfrConfig.JfrRuntimeMetrics) runtimeTelemetry.getJfrTelemetry();
    assertThat(jfrRuntimeMetrics.getRecordedEventHandlers())
        .singleElement()
        .satisfies(
            handler -> {
              assertThat(handler.getEventName()).isEqualTo("jdk.CPULoad");
              assertThat(handler.getMetricNames()).containsExactly("jvm.cpu.recent_utilization");
            });
  }

  @Test
  void setJfrMetricsCopiesPatterns() {
    List<String> patterns = new ArrayList<>(singletonList("jvm.cpu.longlock"));
    RuntimeTelemetryBuilder builder = RuntimeTelemetry.builder(OpenTelemetry.noop());
    Experimental.setJfrMetrics(builder, patterns);
    patterns.clear();
    RuntimeTelemetry runtimeTelemetry = builder.build();
    cleanup.deferCleanup(runtimeTelemetry);

    JfrConfig.JfrRuntimeMetrics jfrRuntimeMetrics =
        (JfrConfig.JfrRuntimeMetrics) runtimeTelemetry.getJfrTelemetry();
    assertThat(jfrRuntimeMetrics.getMetricNames()).containsExactly("jvm.cpu.longlock");
  }

  @Test
  void experimentalJfrMetricsAreUnionedWithSelector() {
    RuntimeTelemetryBuilder builder = RuntimeTelemetry.builder(OpenTelemetry.noop());
    Experimental.setJfrMetrics(builder, singletonList("jvm.class.count"));
    Experimental.setEmitExperimentalJfrMetrics(builder, true);
    RuntimeTelemetry runtimeTelemetry = builder.build();
    cleanup.deferCleanup(runtimeTelemetry);

    JfrConfig.JfrRuntimeMetrics jfrRuntimeMetrics =
        (JfrConfig.JfrRuntimeMetrics) runtimeTelemetry.getJfrTelemetry();
    assertThat(jfrRuntimeMetrics.getMetricNames())
        .contains("jvm.class.count", "jvm.cpu.longlock", "jvm.memory.allocation");
  }

  @Test
  @SuppressWarnings("deprecation") // testing deprecated method
  void deprecatedPreferJfrMetricsSelectsOverlappingMetrics() {
    RuntimeTelemetryBuilder builder = RuntimeTelemetry.builder(OpenTelemetry.noop());
    Experimental.setPreferJfrMetrics(builder, true);
    RuntimeTelemetry runtimeTelemetry = builder.build();
    cleanup.deferCleanup(runtimeTelemetry);

    JfrConfig.JfrRuntimeMetrics jfrRuntimeMetrics =
        (JfrConfig.JfrRuntimeMetrics) runtimeTelemetry.getJfrTelemetry();
    assertThat(jfrRuntimeMetrics.getMetricNames())
        .contains("jvm.class.count", "jvm.cpu.recent_utilization", "jvm.thread.count")
        .doesNotContain("jvm.cpu.longlock", "jvm.memory.allocation");
  }

  @Test
  @SuppressWarnings("deprecation") // testing deprecated method
  void deprecatedPreferJfrMetricsDisabledSelectsNothing() {
    RuntimeTelemetryBuilder builder = RuntimeTelemetry.builder(OpenTelemetry.noop());
    Experimental.setPreferJfrMetrics(builder, false);
    RuntimeTelemetry runtimeTelemetry = builder.build();
    cleanup.deferCleanup(runtimeTelemetry);

    assertThat(runtimeTelemetry.getJfrTelemetry()).isNull();
  }

  @Test
  void splitsMemoryMetricsBetweenJfrAndJmx() {
    TestTelemetry telemetry = buildTelemetry(singletonList("jvm.memory.used"), false);

    assertMetricScopes(telemetry.reader.collectAllMetrics(), "jvm.memory.used", "jfr");
    assertMetricScopes(telemetry.reader.collectAllMetrics(), "jvm.memory.limit", "jmx");
  }

  @Test
  void allJfrMetricsKeepsJmxOnlyMetrics() {
    TestTelemetry telemetry = buildTelemetry(singletonList("*"), false);
    Collection<MetricData> metrics = telemetry.reader.collectAllMetrics();

    assertMetricScopes(metrics, "jvm.cpu.time", "jmx");
  }

  private TestTelemetry buildTelemetry(List<String> jfrMetricPatterns, boolean experimentalJmx) {
    InMemoryMetricReader reader = InMemoryMetricReader.create();
    SdkMeterProvider meterProvider =
        SdkMeterProvider.builder().registerMetricReader(reader).build();
    OpenTelemetrySdk sdk = OpenTelemetrySdk.builder().setMeterProvider(meterProvider).build();
    cleanup.deferCleanup(sdk);

    RuntimeTelemetryBuilder builder = RuntimeTelemetry.builder(sdk);
    Experimental.setJfrMetrics(builder, jfrMetricPatterns);
    Experimental.setEmitExperimentalMetrics(builder, experimentalJmx);
    Internal.setJmxInstrumentationName(builder, "jmx");
    Internal.setJfrInstrumentationName(builder, "jfr");
    RuntimeTelemetry runtimeTelemetry = builder.build();
    cleanup.deferCleanup(runtimeTelemetry);
    return new TestTelemetry(reader);
  }

  private static void assertMetricScopes(
      Collection<MetricData> metrics, String metricName, String... expectedScopes) {
    assertThat(metrics)
        .filteredOn(metric -> metric.getName().equals(metricName))
        .extracting(metric -> metric.getInstrumentationScopeInfo().getName())
        .containsExactlyInAnyOrder(expectedScopes);
  }

  private static final class TestTelemetry {
    private final InMemoryMetricReader reader;

    private TestTelemetry(InMemoryMetricReader reader) {
      this.reader = reader;
    }
  }
}
