/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimetelemetry;

import static io.opentelemetry.semconv.SchemaUrls.V1_44_0;
import static java.util.Arrays.asList;
import static java.util.Collections.emptySet;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

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
import java.util.Set;
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
  void incompleteJfrMemoryMetricFallsBackToJmx() {
    TestTelemetry telemetry = buildTelemetry(include("jvm.memory.used"), false);

    assertThat(telemetry.jfrMetricNames).doesNotContain("jvm.memory.used", "jvm.memory.limit");
    assertMetricScopes(telemetry.reader.collectAllMetrics(), "jvm.memory.used");
    assertMetricScopes(telemetry.reader.collectAllMetrics(), "jvm.memory.limit");
  }

  @Test
  void incompleteJfrMemoryInitFallsBackToEnabledJmxMetric() {
    TestTelemetry telemetry = buildTelemetry(include("jvm.memory.init"), true);

    assertThat(telemetry.jfrMetricNames).doesNotContain("jvm.memory.init");
    assertMetricScopes(telemetry.reader.collectAllMetrics(), "jvm.memory.init");
  }

  @Test
  void jfrMemoryInitIsAllowedWhenExperimentalJmxMetricsAreDisabled() {
    TestTelemetry telemetry = buildTelemetry(include("jvm.memory.init"), false);

    assertThat(telemetry.jfrMetricNames).contains("jvm.memory.init");
    assertMetricScopes(telemetry.reader.collectAllMetrics(), "jvm.memory.init");
  }

  @Test
  void incompleteJfrBufferMetricFallsBackToEnabledJmxMetric() {
    TestTelemetry telemetry = buildTelemetry(include("jvm.buffer.count"), true);

    assertThat(telemetry.jfrMetricNames).doesNotContain("jvm.buffer.count");
    assertMetricScopes(telemetry.reader.collectAllMetrics(), "jvm.buffer.count");
  }

  @Test
  void jfrBufferMetricIsAllowedWhenExperimentalJmxMetricsAreDisabled() {
    TestTelemetry telemetry = buildTelemetry(include("jvm.buffer.count"), false);

    assertThat(telemetry.jfrMetricNames).contains("jvm.buffer.count");
    assertMetricScopes(telemetry.reader.collectAllMetrics(), "jvm.buffer.count");
  }

  @Test
  void allJfrMetricsKeepsJmxOnlyMetrics() {
    TestTelemetry telemetry = buildTelemetry(include("*"), false);
    Collection<MetricData> metrics = telemetry.reader.collectAllMetrics();

    assertThat(telemetry.jfrMetricNames).doesNotContain("jvm.cpu.time");
    assertMetricSchemaUrl(metrics, "jvm.cpu.time", V1_44_0);
  }

  @Test
  void defaultJmxMetricsUseSemconvSchemaUrl() {
    TestTelemetry telemetry = buildTelemetry(include("not.a.jvm.metric"), false);

    assertMetricSchemaUrl(telemetry.reader.collectAllMetrics(), "jvm.memory.used", V1_44_0);
  }

  @Test
  void jfrMetricsCoveredBySchemaUseSchemaUrlWithoutJmx() {
    TestTelemetry telemetry =
        buildTelemetry(include("jvm.cpu.recent_utilization"), false, true, false);

    await()
        .untilAsserted(
            () ->
                assertMetricSchemaUrl(
                    telemetry.reader.collectAllMetrics(), "jvm.cpu.recent_utilization", V1_44_0));
  }

  @Test
  void excludingJfrMetricsNotCoveredBySchemaAllowsSchemaUrl() {
    TestTelemetry telemetry =
        buildTelemetry(
            IncludeExclude.builder()
                .setIncluded(singletonList("jvm.cpu.*"))
                .setExcluded(asList("jvm.cpu.context_switch", "jvm.cpu.longlock"))
                .build(),
            false);

    await()
        .untilAsserted(
            () ->
                assertMetricSchemaUrl(
                    telemetry.reader.collectAllMetrics(), "jvm.cpu.recent_utilization", V1_44_0));
  }

  @Test
  void jfrSelectionWithUncoveredMetricKeepsSchemaOnCoveredMetric() {
    TestTelemetry telemetry =
        buildTelemetry(include("jvm.cpu.recent_utilization", "jvm.cpu.context_switch"), false);

    await()
        .untilAsserted(
            () ->
                assertMetricSchemaUrl(
                    telemetry.reader.collectAllMetrics(), "jvm.cpu.recent_utilization", V1_44_0));
    assertMetricSchemaUrl(telemetry.reader.collectAllMetrics(), "jvm.cpu.context_switch", null);
    assertMetricSchemaUrl(telemetry.reader.collectAllMetrics(), "jvm.cpu.time", V1_44_0);
  }

  @Test
  void experimentalJfrSelectionKeepsSchemaOnCoveredMetrics() {
    TestTelemetry telemetry =
        buildTelemetry(include("jvm.cpu.recent_utilization"), false, false, true);

    await()
        .untilAsserted(
            () ->
                assertMetricSchemaUrl(
                    telemetry.reader.collectAllMetrics(), "jvm.cpu.recent_utilization", V1_44_0));
    assertMetricSchemaUrl(telemetry.reader.collectAllMetrics(), "jvm.cpu.context_switch", null);
  }

  @Test
  void jfrCpuCountUsesSemconvSchemaUrl() {
    TestTelemetry telemetry = buildTelemetry(include("jvm.cpu.count"), false);

    assertThat(telemetry.jfrMetricNames).contains("jvm.cpu.count");
    assertMetricSchemaUrl(telemetry.reader.collectAllMetrics(), "jvm.cpu.count", V1_44_0);
  }

  private TestTelemetry buildTelemetry(IncludeExclude jfrMetrics, boolean experimentalJmx) {
    return buildTelemetry(jfrMetrics, experimentalJmx, false, false);
  }

  private TestTelemetry buildTelemetry(
      IncludeExclude jfrMetrics,
      boolean experimentalJmx,
      boolean disableJmx,
      boolean experimentalJfr) {
    InMemoryMetricReader reader = InMemoryMetricReader.create();
    SdkMeterProvider meterProvider =
        SdkMeterProvider.builder().registerMetricReader(reader).build();
    OpenTelemetrySdk sdk = OpenTelemetrySdk.builder().setMeterProvider(meterProvider).build();
    cleanup.deferCleanup(sdk);

    RuntimeTelemetryBuilder builder = RuntimeTelemetry.builder(sdk);
    Experimental.setJfrMetrics(builder, jfrMetrics);
    Experimental.setEmitExperimentalMetrics(builder, experimentalJmx);
    Experimental.setEmitExperimentalJfrMetrics(builder, experimentalJfr);
    Internal.setDisableJmx(builder, disableJmx);
    RuntimeTelemetry runtimeTelemetry = builder.build();
    cleanup.deferCleanup(runtimeTelemetry);
    JfrConfig.JfrRuntimeMetrics jfrTelemetry =
        (JfrConfig.JfrRuntimeMetrics) runtimeTelemetry.getJfrTelemetry();
    return new TestTelemetry(
        reader, jfrTelemetry == null ? emptySet() : jfrTelemetry.getMetricNames());
  }

  private static IncludeExclude include(String... patterns) {
    return IncludeExclude.builder().setIncluded(asList(patterns)).build();
  }

  private static void assertMetricScopes(Collection<MetricData> metrics, String metricName) {
    assertThat(metrics)
        .filteredOn(metric -> metric.getName().equals(metricName))
        .extracting(metric -> metric.getInstrumentationScopeInfo().getName())
        .containsExactly("io.opentelemetry.runtime-telemetry");
  }

  private static void assertMetricSchemaUrl(
      Collection<MetricData> metrics, String metricName, String schemaUrl) {
    assertMetricScopes(metrics, metricName);
    assertThat(metrics)
        .filteredOn(metric -> metric.getName().equals(metricName))
        .extracting(metric -> metric.getInstrumentationScopeInfo().getSchemaUrl())
        .containsExactly(schemaUrl);
  }

  private static final class TestTelemetry {
    private final InMemoryMetricReader reader;
    private final Set<String> jfrMetricNames;

    private TestTelemetry(InMemoryMetricReader reader, Set<String> jfrMetricNames) {
      this.reader = reader;
      this.jfrMetricNames = jfrMetricNames;
    }
  }
}
