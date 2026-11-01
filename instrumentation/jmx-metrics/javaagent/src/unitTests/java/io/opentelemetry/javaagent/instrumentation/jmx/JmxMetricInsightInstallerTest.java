/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jmx;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.config.bridge.DeclarativeConfigBridge;
import io.opentelemetry.instrumentation.jmx.JmxTelemetry;
import io.opentelemetry.instrumentation.jmx.JmxTelemetryBuilder;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.autoconfigure.spi.internal.DefaultConfigProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

class JmxMetricInsightInstallerTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  @Test
  void stableDefaults() {
    JmxTelemetryBuilder builder = mock(JmxTelemetryBuilder.class);
    JmxMetricInsightInstaller.configure(builder, config(emptyMap()));

    verify(builder)
        .setInternalMetricsSystemFilter(IncludeExclude.builder().setExcluded("jvm").build());
    verify(builder)
        .setInternalMetricsUnstableMetricsFilter(IncludeExclude.builder().setExcluded("*").build());
  }

  @Test
  void emptyExperimentalInclusion() {
    JmxTelemetryBuilder builder = JmxTelemetry.builder(testing.getOpenTelemetry());
    JmxMetricInsightInstaller.configure(
        builder, config(singletonMap("otel.jmx.metrics.experimental.included", "")));
    cleanup.deferCleanup(builder.build().start());

    testing.waitAndAssertMetrics(
        "io.opentelemetry.jmx", metric -> metric.hasName("tomcat.test.stable"));
  }

  @Test
  void experimentalInclusion() {
    JmxTelemetryBuilder builder = mock(JmxTelemetryBuilder.class);
    JmxMetricInsightInstaller.configure(
        builder,
        config(singletonMap("otel.jmx.metrics.experimental.included", "tomcat.*,kafka.*")));

    verify(builder)
        .setInternalMetricsSystemFilter(IncludeExclude.builder().setExcluded("jvm").build());
    verify(builder)
        .setInternalMetricsUnstableMetricsFilter(
            IncludeExclude.builder().setIncluded("tomcat.*", "kafka.*").build());
  }

  @Test
  void normalFilters() {
    Map<String, String> properties = new HashMap<>();
    properties.put("otel.jmx.metrics.included", "tomcat.*");
    properties.put("otel.jmx.metrics.excluded", "tomcat.test.unstable");
    JmxTelemetryBuilder builder = mock(JmxTelemetryBuilder.class);
    JmxMetricInsightInstaller.configure(builder, config(properties));

    verify(builder)
        .setMetrics(
            IncludeExclude.builder()
                .setIncluded("tomcat.*")
                .setExcluded("tomcat.test.unstable")
                .build());
  }

  @Test
  void collectsStableMetricsByDefault() {
    JmxTelemetryBuilder builder = JmxTelemetry.builder(testing.getOpenTelemetry());
    JmxMetricInsightInstaller.configure(builder, config(emptyMap()));
    cleanup.deferCleanup(builder.build().start());

    testing.waitAndAssertMetrics(
        "io.opentelemetry.jmx", metric -> metric.hasName("tomcat.test.stable"));
  }

  @Test
  void collectsOptedInMetrics() {
    JmxTelemetryBuilder builder = JmxTelemetry.builder(testing.getOpenTelemetry());
    JmxMetricInsightInstaller.configure(
        builder, config(singletonMap("otel.jmx.metrics.experimental.included", "tomcat.*")));
    cleanup.deferCleanup(builder.build().start());

    testing.waitAndAssertMetrics(
        "io.opentelemetry.jmx",
        metric ->
            metric
                .hasName("tomcat.test.stable")
                .satisfies(
                    data -> assertThat(data.getInstrumentationScopeInfo().getSchemaUrl()).isNull()),
        metric ->
            metric
                .hasName("tomcat.test.unstable")
                .satisfies(
                    data ->
                        assertThat(data.getInstrumentationScopeInfo().getSchemaUrl()).isNull()));
  }

  @Test
  void excludesOptedInMetrics() {
    Map<String, String> properties = new HashMap<>();
    properties.put("otel.jmx.metrics.experimental.included", "tomcat.*");
    properties.put("otel.jmx.metrics.included", "tomcat.*");
    properties.put("otel.jmx.metrics.excluded", "tomcat.test.unstable");
    JmxTelemetryBuilder builder = JmxTelemetry.builder(testing.getOpenTelemetry());
    JmxMetricInsightInstaller.configure(builder, config(properties));
    cleanup.deferCleanup(builder.build().start());

    testing.waitAndAssertMetrics(
        "io.opentelemetry.jmx", metric -> metric.hasName("tomcat.test.stable"));
  }

  @Test
  void customFileDoesNotRequireExperimentalInclusion(@TempDir Path directory) throws IOException {
    Path rules = directory.resolve("custom-jmx.yaml");
    Files.write(
        rules,
        ("rules:\n"
                + "  - bean: java.lang:type=Threading\n"
                + "    metricAttribute:\n"
                + "      test.source: const(custom)\n"
                + "    mapping:\n"
                + "      ThreadCount:\n"
                + "        metric: tomcat.test.unstable\n"
                + "        type: gauge\n"
                + "        unit: '{thread}'\n"
                + "        desc: Current number of threads.\n")
            .getBytes(UTF_8));
    JmxTelemetryBuilder builder = JmxTelemetry.builder(testing.getOpenTelemetry());
    JmxMetricInsightInstaller.configure(
        builder, config(singletonMap("otel.jmx.config", rules.toString())));
    cleanup.deferCleanup(builder.build().start());

    testing.waitAndAssertMetrics(
        "io.opentelemetry.jmx",
        metric -> metric.hasName("tomcat.test.stable"),
        metric ->
            metric
                .hasName("tomcat.test.unstable")
                .hasLongGaugeSatisfying(
                    gauge ->
                        gauge.hasPointsSatisfying(
                            point ->
                                point.hasAttributes(
                                    Attributes.of(stringKey("test.source"), "custom")))));
  }

  @Test
  void customFileUsesNormalExclusions(@TempDir Path directory) throws IOException {
    Path rules = directory.resolve("custom-jmx.yaml");
    Files.write(
        rules,
        ("rules:\n"
                + "  - bean: java.lang:type=Threading\n"
                + "    mapping:\n"
                + "      ThreadCount:\n"
                + "        metric: custom.test.excluded\n"
                + "        type: gauge\n"
                + "        unit: '{thread}'\n")
            .getBytes(UTF_8));
    Map<String, String> properties = new HashMap<>();
    properties.put("otel.jmx.config", rules.toString());
    properties.put("otel.jmx.metrics.excluded", "custom.*");
    JmxTelemetryBuilder builder = JmxTelemetry.builder(testing.getOpenTelemetry());
    JmxMetricInsightInstaller.configure(builder, config(properties));
    cleanup.deferCleanup(builder.build().start());

    testing.waitAndAssertMetrics(
        "io.opentelemetry.jmx", metric -> metric.hasName("tomcat.test.stable"));
  }

  private static DeclarativeConfigProperties config(Map<String, String> properties) {
    return DeclarativeConfigBridge.createInstrumentationConfig(
            DefaultConfigProperties.createFromMap(properties))
        .getInstrumentationConfig()
        .get("java")
        .get("jmx");
  }
}
