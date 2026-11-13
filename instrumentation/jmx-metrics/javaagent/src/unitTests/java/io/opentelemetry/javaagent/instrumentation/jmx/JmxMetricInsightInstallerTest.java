/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jmx;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.common.ComponentLoader;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.config.bridge.DeclarativeConfigBridge;
import io.opentelemetry.instrumentation.jmx.JmxTelemetry;
import io.opentelemetry.instrumentation.jmx.JmxTelemetryBuilder;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.DeclarativeConfiguration;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.YamlDeclarativeConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.internal.DefaultConfigProperties;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.assertj.core.api.AbstractIterableAssert;
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
  void customRulesFromDeclarativeConfig() throws IOException {
    DeclarativeConfigProperties config;
    try (InputStream input = getClass().getResourceAsStream("/jmx-declarative-config.yaml")) {
      config =
          DeclarativeConfiguration.toConfigProperties(DeclarativeConfiguration.parse(input))
              .get("instrumentation/development")
              .get("java")
              .get("jmx");
    }
    JmxTelemetryBuilder builder = JmxTelemetry.builder(testing.getOpenTelemetry());
    JmxMetricInsightInstaller.configure(builder, config);
    cleanup.deferCleanup(builder.build().start());

    testing.waitAndAssertMetrics(
        "io.opentelemetry.jmx",
        metric -> metric.hasName("tomcat.test.unstable"),
        metric ->
            metric
                .hasName("custom.test.embedded")
                .hasUnit("{thread}")
                .hasDescription("Current number of threads.")
                .hasLongGaugeSatisfying(
                    gauge ->
                        gauge.hasPointsSatisfying(
                            point ->
                                point.hasAttributes(
                                    Attributes.of(stringKey("test.source"), "declarative")))));

    testing.waitAndAssertMetrics(
        "io.opentelemetry.jmx", "custom.test.excluded", metrics -> metrics.isEmpty());
    testing.waitAndAssertMetrics(
        "io.opentelemetry.jmx", "other.test.not-included", metrics -> metrics.isEmpty());
    testing.waitAndAssertMetrics(
        "io.opentelemetry.jmx", "tomcat.test.stable", metrics -> metrics.isEmpty());
  }

  @Test
  void invalidInlineRulesDoNotPreventOtherMetrics(@TempDir Path directory) throws IOException {
    Path rules = directory.resolve("custom-jmx.yaml");
    Files.write(
        rules,
        ("rules:\n"
                + "  - bean: java.lang:type=Threading\n"
                + "    mapping:\n"
                + "      ThreadCount:\n"
                + "        metric: custom.test.from-file\n"
                + "        type: gauge\n"
                + "        unit: '{thread}'\n")
            .getBytes(UTF_8));
    // This is an invalid inline metric definition that will trigger error
    Map<String, Object> inlineMetric = new HashMap<>();
    inlineMetric.put("metric", "custom.test.invalid-inline");
    inlineMetric.put("type", "invalid");
    inlineMetric.put("unit", "{thread}");
    Map<String, Object> inlineRule = new HashMap<>();
    inlineRule.put("bean", "java.lang:type=Threading");
    inlineRule.put("mapping", singletonMap("ThreadCount", inlineMetric));

    Map<String, Object> properties = new HashMap<>();
    properties.put("config", singletonList(rules.toString()));
    properties.put("rules", singletonList(inlineRule));
    DeclarativeConfigProperties config =
        YamlDeclarativeConfigProperties.create(
            properties, ComponentLoader.forClassLoader(getClass().getClassLoader()));

    JmxTelemetryBuilder builder = JmxTelemetry.builder(testing.getOpenTelemetry());
    JmxMetricInsightInstaller.configure(builder, config);
    cleanup.deferCleanup(builder.build().start());

    // Bundled and file-defined metrics must still be emitted when the inline rule is rejected.
    testing.waitAndAssertMetrics(
        "io.opentelemetry.jmx",
        metric -> metric.hasName("tomcat.test.stable"),
        metric -> metric.hasName("custom.test.from-file").hasUnit("{thread}"));

    testing.waitAndAssertMetrics(
        "io.opentelemetry.jmx", "custom.test.invalid-inline", AbstractIterableAssert::isEmpty);
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
