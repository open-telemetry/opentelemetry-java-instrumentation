/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimetelemetry;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.runtimetelemetry.internal.Experimental;
import io.opentelemetry.instrumentation.runtimetelemetry.internal.Internal;
import io.opentelemetry.instrumentation.runtimetelemetry.internal.JfrConfig;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import java.util.Collection;
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
    Experimental.setJfrMetrics(builder, include("jvm.cpu.longlock"));
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
    Experimental.setJfrMetrics(builder, include("jvm.cpu.recent_utilization"));
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
  void globPatternsSelectMetrics() {
    RuntimeTelemetryBuilder builder = RuntimeTelemetry.builder(OpenTelemetry.noop());
    Experimental.setJfrMetrics(builder, include("jvm.cpu.long*", "jvm.class.coun?"));
    RuntimeTelemetry runtimeTelemetry = builder.build();
    cleanup.deferCleanup(runtimeTelemetry);

    JfrConfig.JfrRuntimeMetrics jfrRuntimeMetrics =
        (JfrConfig.JfrRuntimeMetrics) runtimeTelemetry.getJfrTelemetry();
    assertThat(jfrRuntimeMetrics.getMetricNames())
        .containsExactlyInAnyOrder("jvm.cpu.longlock", "jvm.class.count");
  }

  @Test
  void selectorMatchingNoMetricsDoesNotStartRecording() {
    RuntimeTelemetryBuilder builder = RuntimeTelemetry.builder(OpenTelemetry.noop());
    Experimental.setJfrMetrics(builder, include("not.a.jvm.metric"));
    RuntimeTelemetry runtimeTelemetry = builder.build();
    cleanup.deferCleanup(runtimeTelemetry);

    assertThat(runtimeTelemetry.getJfrTelemetry()).isNull();
  }

  @Test
  void emptySelectorSelectsNothing() {
    RuntimeTelemetryBuilder builder = RuntimeTelemetry.builder(OpenTelemetry.noop());
    Experimental.setJfrMetrics(builder, IncludeExclude.builder().build());
    RuntimeTelemetry runtimeTelemetry = builder.build();
    cleanup.deferCleanup(runtimeTelemetry);

    assertThat(runtimeTelemetry.getJfrTelemetry()).isNull();
  }

  @Test
  void excludeOnlySelectsAllOtherMetrics() {
    RuntimeTelemetryBuilder builder = RuntimeTelemetry.builder(OpenTelemetry.noop());
    Experimental.setJfrMetrics(
        builder, IncludeExclude.builder().setExcluded(singletonList("jvm.memory.*")).build());
    RuntimeTelemetry runtimeTelemetry = builder.build();
    cleanup.deferCleanup(runtimeTelemetry);

    JfrConfig.JfrRuntimeMetrics jfrRuntimeMetrics =
        (JfrConfig.JfrRuntimeMetrics) runtimeTelemetry.getJfrTelemetry();
    assertThat(jfrRuntimeMetrics.getMetricNames())
        .contains("jvm.class.count", "jvm.cpu.longlock")
        .noneMatch(name -> name.startsWith("jvm.memory."));
  }

  @Test
  void exclusionsTakePrecedenceOverIncludes() {
    RuntimeTelemetryBuilder builder = RuntimeTelemetry.builder(OpenTelemetry.noop());
    Experimental.setJfrMetrics(
        builder,
        IncludeExclude.builder()
            .setIncluded(singletonList("jvm.cpu.*"))
            .setExcluded(singletonList("jvm.cpu.longlock"))
            .build());
    RuntimeTelemetry runtimeTelemetry = builder.build();
    cleanup.deferCleanup(runtimeTelemetry);

    JfrConfig.JfrRuntimeMetrics jfrRuntimeMetrics =
        (JfrConfig.JfrRuntimeMetrics) runtimeTelemetry.getJfrTelemetry();
    assertThat(jfrRuntimeMetrics.getMetricNames())
        .contains("jvm.cpu.recent_utilization")
        .doesNotContain("jvm.cpu.longlock");
  }

  @Test
  void experimentalJfrMetricsAreUnionedWithSelector() {
    RuntimeTelemetryBuilder builder = RuntimeTelemetry.builder(OpenTelemetry.noop());
    Experimental.setJfrMetrics(builder, include("jvm.class.count"));
    Experimental.setEmitExperimentalJfrMetrics(builder, true);
    RuntimeTelemetry runtimeTelemetry = builder.build();
    cleanup.deferCleanup(runtimeTelemetry);

    JfrConfig.JfrRuntimeMetrics jfrRuntimeMetrics =
        (JfrConfig.JfrRuntimeMetrics) runtimeTelemetry.getJfrTelemetry();
    assertThat(jfrRuntimeMetrics.getMetricNames())
        .contains("jvm.class.count", "jvm.cpu.longlock", "jvm.memory.allocation");
  }

  @Test
  void experimentalJfrMetricsIncludeBufferMetrics() {
    RuntimeTelemetryBuilder builder = RuntimeTelemetry.builder(OpenTelemetry.noop());
    Experimental.setEmitExperimentalJfrMetrics(builder, true);
    RuntimeTelemetry runtimeTelemetry = builder.build();
    cleanup.deferCleanup(runtimeTelemetry);

    JfrConfig.JfrRuntimeMetrics jfrRuntimeMetrics =
        (JfrConfig.JfrRuntimeMetrics) runtimeTelemetry.getJfrTelemetry();
    assertThat(jfrRuntimeMetrics.getMetricNames())
        .contains("jvm.buffer.count", "jvm.buffer.memory.limit", "jvm.buffer.memory.used");
  }

  @Test
  void exclusionsTakePrecedenceOverExperimentalJfrMetrics() {
    RuntimeTelemetryBuilder builder = RuntimeTelemetry.builder(OpenTelemetry.noop());
    Experimental.setJfrMetrics(
        builder,
        IncludeExclude.builder()
            .setIncluded(singletonList("jvm.class.count"))
            .setExcluded(singletonList("jvm.cpu.longlock"))
            .build());
    Experimental.setEmitExperimentalJfrMetrics(builder, true);
    RuntimeTelemetry runtimeTelemetry = builder.build();
    cleanup.deferCleanup(runtimeTelemetry);

    JfrConfig.JfrRuntimeMetrics jfrRuntimeMetrics =
        (JfrConfig.JfrRuntimeMetrics) runtimeTelemetry.getJfrTelemetry();
    assertThat(jfrRuntimeMetrics.getMetricNames())
        .contains("jvm.class.count", "jvm.memory.allocation")
        .doesNotContain("jvm.cpu.longlock");
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
    Experimental.setPreferJfrMetrics(builder, true);
    Experimental.setPreferJfrMetrics(builder, false);
    RuntimeTelemetry runtimeTelemetry = builder.build();
    cleanup.deferCleanup(runtimeTelemetry);

    assertThat(runtimeTelemetry.getJfrTelemetry()).isNull();
  }

  @Test
  @SuppressWarnings("deprecation") // testing deprecated method
  void deprecatedPreferJfrMetricsMergesWithExplicitSelector() {
    RuntimeTelemetryBuilder builder = RuntimeTelemetry.builder(OpenTelemetry.noop());
    Experimental.setJfrMetrics(
        builder,
        IncludeExclude.builder()
            .setIncluded(singletonList("jvm.cpu.longlock"))
            .setExcluded(singletonList("jvm.class.count"))
            .build());
    Experimental.setPreferJfrMetrics(builder, true);
    RuntimeTelemetry runtimeTelemetry = builder.build();
    cleanup.deferCleanup(runtimeTelemetry);

    JfrConfig.JfrRuntimeMetrics jfrRuntimeMetrics =
        (JfrConfig.JfrRuntimeMetrics) runtimeTelemetry.getJfrTelemetry();
    assertThat(jfrRuntimeMetrics.getMetricNames())
        .contains("jvm.cpu.longlock", "jvm.thread.count")
        .doesNotContain("jvm.class.count");
  }

  @Test
  @SuppressWarnings("deprecation") // testing deprecated method
  void explicitSelectorMergesWithDeprecatedPreferJfrMetrics() {
    RuntimeTelemetryBuilder builder = RuntimeTelemetry.builder(OpenTelemetry.noop());
    Experimental.setPreferJfrMetrics(builder, true);
    Experimental.setJfrMetrics(
        builder,
        IncludeExclude.builder()
            .setIncluded(singletonList("jvm.cpu.longlock"))
            .setExcluded(singletonList("jvm.class.count"))
            .build());
    RuntimeTelemetry runtimeTelemetry = builder.build();
    cleanup.deferCleanup(runtimeTelemetry);

    JfrConfig.JfrRuntimeMetrics jfrRuntimeMetrics =
        (JfrConfig.JfrRuntimeMetrics) runtimeTelemetry.getJfrTelemetry();
    assertThat(jfrRuntimeMetrics.getMetricNames())
        .contains("jvm.cpu.longlock", "jvm.thread.count")
        .doesNotContain("jvm.class.count");
  }

  @Test
  void incompleteJfrMemoryMetricFallsBackToJmx() {
    TestTelemetry telemetry = buildTelemetry(include("jvm.memory.used"), false);

    assertMetricScopes(telemetry.reader.collectAllMetrics(), "jvm.memory.used", "jmx");
    assertMetricScopes(telemetry.reader.collectAllMetrics(), "jvm.memory.limit", "jmx");
  }

  @Test
  void incompleteJfrMemoryInitFallsBackToEnabledJmxMetric() {
    TestTelemetry telemetry = buildTelemetry(include("jvm.memory.init"), true);

    assertMetricScopes(telemetry.reader.collectAllMetrics(), "jvm.memory.init", "jmx");
  }

  @Test
  void jfrMemoryInitIsAllowedWhenExperimentalJmxMetricsAreDisabled() {
    TestTelemetry telemetry = buildTelemetry(include("jvm.memory.init"), false);

    assertMetricScopes(telemetry.reader.collectAllMetrics(), "jvm.memory.init", "jfr");
  }

  @Test
  void incompleteJfrBufferMetricFallsBackToEnabledJmxMetric() {
    TestTelemetry telemetry = buildTelemetry(include("jvm.buffer.count"), true);

    assertMetricScopes(telemetry.reader.collectAllMetrics(), "jvm.buffer.count", "jmx");
  }

  @Test
  void jfrBufferMetricIsAllowedWhenExperimentalJmxMetricsAreDisabled() {
    TestTelemetry telemetry = buildTelemetry(include("jvm.buffer.count"), false);

    assertMetricScopes(telemetry.reader.collectAllMetrics(), "jvm.buffer.count", "jfr");
  }

  @Test
  void allJfrMetricsKeepsJmxOnlyMetrics() {
    TestTelemetry telemetry = buildTelemetry(include("*"), false);
    Collection<MetricData> metrics = telemetry.reader.collectAllMetrics();

    assertMetricScopes(metrics, "jvm.cpu.time", "jmx");
  }

  private TestTelemetry buildTelemetry(IncludeExclude jfrMetrics, boolean experimentalJmx) {
    InMemoryMetricReader reader = InMemoryMetricReader.create();
    SdkMeterProvider meterProvider =
        SdkMeterProvider.builder().registerMetricReader(reader).build();
    OpenTelemetrySdk sdk = OpenTelemetrySdk.builder().setMeterProvider(meterProvider).build();
    cleanup.deferCleanup(sdk);

    RuntimeTelemetryBuilder builder = RuntimeTelemetry.builder(sdk);
    Experimental.setJfrMetrics(builder, jfrMetrics);
    Experimental.setEmitExperimentalMetrics(builder, experimentalJmx);
    Internal.setJmxInstrumentationName(builder, "jmx");
    Internal.setJfrInstrumentationName(builder, "jfr");
    RuntimeTelemetry runtimeTelemetry = builder.build();
    cleanup.deferCleanup(runtimeTelemetry);
    return new TestTelemetry(reader);
  }

  private static IncludeExclude include(String... patterns) {
    return IncludeExclude.builder().setIncluded(asList(patterns)).build();
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
