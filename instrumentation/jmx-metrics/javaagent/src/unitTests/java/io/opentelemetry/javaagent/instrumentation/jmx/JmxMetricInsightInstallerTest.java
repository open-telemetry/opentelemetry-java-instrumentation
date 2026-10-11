/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jmx;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
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
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.DeclarativeConfiguration;
import io.opentelemetry.sdk.autoconfigure.spi.internal.DefaultConfigProperties;
import io.opentelemetry.sdk.internal.SdkConfigProvider;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class JmxMetricInsightInstallerTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void stableDefaults(boolean declarative) {
    JmxTelemetryBuilder builder = mock(JmxTelemetryBuilder.class);
    JmxMetricInsightInstaller.configure(builder, config(declarative, emptyMap(), "{}"));

    verify(builder)
        .setInternalMetricsSystemFilter(IncludeExclude.builder().setExcluded("jvm").build());
    verify(builder)
        .setInternalMetricsUnstableMetricsFilter(IncludeExclude.builder().setExcluded("*").build());
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void emptyExperimentalInclusion(boolean declarative) {
    JmxTelemetryBuilder builder = JmxTelemetry.builder(testing.getOpenTelemetry());
    JmxMetricInsightInstaller.configure(
        builder,
        config(
            declarative,
            singletonMap("otel.jmx.metrics.experimental.included", ""),
            "metrics:\n  experimental:\n    included: []"));
    cleanup.deferCleanup(builder.build().start());

    testing.waitAndAssertMetrics(
        "io.opentelemetry.jmx", metric -> metric.hasName("tomcat.test.stable"));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void experimentalInclusion(boolean declarative) {
    JmxTelemetryBuilder builder = mock(JmxTelemetryBuilder.class);
    JmxMetricInsightInstaller.configure(
        builder,
        config(
            declarative,
            singletonMap("otel.jmx.metrics.experimental.included", "tomcat.*,kafka.*"),
            "metrics:\n  experimental:\n    included: [tomcat.*, kafka.*]"));

    verify(builder)
        .setInternalMetricsSystemFilter(IncludeExclude.builder().setExcluded("jvm").build());
    verify(builder)
        .setInternalMetricsUnstableMetricsFilter(
            IncludeExclude.builder().setIncluded("tomcat.*", "kafka.*").build());
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void normalFilters(boolean declarative) {
    Map<String, String> properties = new HashMap<>();
    properties.put("otel.jmx.metrics.included", "tomcat.*");
    properties.put("otel.jmx.metrics.excluded", "tomcat.test.unstable");
    JmxTelemetryBuilder builder = mock(JmxTelemetryBuilder.class);
    JmxMetricInsightInstaller.configure(
        builder,
        config(
            declarative,
            properties,
            "metrics:\n  included: [tomcat.*]\n  excluded: [tomcat.test.unstable]"));

    verify(builder)
        .setMetrics(
            IncludeExclude.builder()
                .setIncluded("tomcat.*")
                .setExcluded("tomcat.test.unstable")
                .build());
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void collectsStableMetricsByDefault(boolean declarative) {
    JmxTelemetryBuilder builder = JmxTelemetry.builder(testing.getOpenTelemetry());
    JmxMetricInsightInstaller.configure(builder, config(declarative, emptyMap(), "{}"));
    cleanup.deferCleanup(builder.build().start());

    testing.waitAndAssertMetrics(
        "io.opentelemetry.jmx", metric -> metric.hasName("tomcat.test.stable"));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void collectsOptedInMetrics(boolean declarative) {
    JmxTelemetryBuilder builder = JmxTelemetry.builder(testing.getOpenTelemetry());
    JmxMetricInsightInstaller.configure(
        builder,
        config(
            declarative,
            singletonMap("otel.jmx.metrics.experimental.included", "tomcat.*"),
            "metrics:\n  experimental:\n    included: [tomcat.*]"));
    cleanup.deferCleanup(builder.build().start());

    testing.waitAndAssertMetrics(
        "io.opentelemetry.jmx",
        metric -> metric.hasName("tomcat.test.stable"),
        metric -> metric.hasName("tomcat.test.unstable"));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void excludesOptedInMetrics(boolean declarative) {
    Map<String, String> properties = new HashMap<>();
    properties.put("otel.jmx.metrics.experimental.included", "tomcat.*");
    properties.put("otel.jmx.metrics.included", "tomcat.*");
    properties.put("otel.jmx.metrics.excluded", "tomcat.test.unstable");
    JmxTelemetryBuilder builder = JmxTelemetry.builder(testing.getOpenTelemetry());
    JmxMetricInsightInstaller.configure(
        builder,
        config(
            declarative,
            properties,
            "metrics:\n  experimental:\n    included: [tomcat.*]\n"
                + "  included: [tomcat.*]\n  excluded: [tomcat.test.unstable]"));
    cleanup.deferCleanup(builder.build().start());

    testing.waitAndAssertMetrics(
        "io.opentelemetry.jmx", metric -> metric.hasName("tomcat.test.stable"));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void customFileDoesNotRequireExperimentalInclusion(boolean declarative, @TempDir Path directory)
      throws IOException {
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
        builder,
        config(
            declarative,
            singletonMap("otel.jmx.config", rules.toString()),
            "config: ['" + rules + "']"));
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

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void customFileUsesNormalExclusions(boolean declarative, @TempDir Path directory)
      throws IOException {
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
    JmxMetricInsightInstaller.configure(
        builder,
        config(
            declarative,
            properties,
            "config: ['" + rules + "']\nmetrics:\n  excluded: [custom.*]"));
    cleanup.deferCleanup(builder.build().start());

    testing.waitAndAssertMetrics(
        "io.opentelemetry.jmx", metric -> metric.hasName("tomcat.test.stable"));
  }

  private static DeclarativeConfigProperties config(
      boolean declarative, Map<String, String> properties, String jmxYaml) {
    if (!declarative) {
      return DeclarativeConfigBridge.createInstrumentationConfig(
              DefaultConfigProperties.createFromMap(properties))
          .getInstrumentationConfig()
          .get("java")
          .get("jmx");
    }
    String yaml =
        "file_format: 1.1\n"
            + "instrumentation/development:\n"
            + "  java:\n"
            + "    jmx:\n"
            + "      "
            + jmxYaml.replace("\n", "\n      ")
            + "\n";
    return SdkConfigProvider.create(
            DeclarativeConfiguration.toConfigProperties(
                DeclarativeConfiguration.parse(new ByteArrayInputStream(yaml.getBytes(UTF_8)))))
        .getInstrumentationConfig()
        .get("java")
        .get("jmx");
  }
}
