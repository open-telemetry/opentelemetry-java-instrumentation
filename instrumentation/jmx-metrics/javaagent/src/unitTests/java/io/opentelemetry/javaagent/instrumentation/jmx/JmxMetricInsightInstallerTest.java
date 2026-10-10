/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jmx;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.github.netmikey.logunit.api.LogCapturer;
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
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class JmxMetricInsightInstallerTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  @RegisterExtension
  LogCapturer logs = LogCapturer.create().captureForType(JmxMetricInsightInstaller.class);

  @ParameterizedTest
  @MethodSource("modes")
  void stableDefaults(boolean declarative, boolean v3Preview) {
    JmxTelemetryBuilder builder = mock(JmxTelemetryBuilder.class);
    JmxMetricInsightInstaller.configure(builder, config(declarative, emptyMap(), "{}"), v3Preview);

    verify(builder)
        .setInternalMetricsSystemFilter(IncludeExclude.builder().setExcluded("jvm").build());
    verify(builder)
        .setInternalMetricsUnstableMetricsFilter(IncludeExclude.builder().setExcluded("*").build());
    logs.assertDoesNotContain("is deprecated");
  }

  @ParameterizedTest
  @MethodSource("modes")
  void experimentalInclusion(boolean declarative, boolean v3Preview) {
    JmxTelemetryBuilder builder = mock(JmxTelemetryBuilder.class);
    JmxMetricInsightInstaller.configure(
        builder,
        config(
            declarative,
            singletonMap("otel.jmx.metrics.experimental.included", "tomcat.*,kafka.*"),
            "metrics:\n  experimental:\n    included: [tomcat.*, kafka.*]"),
        v3Preview);

    verify(builder)
        .setInternalMetricsSystemFilter(IncludeExclude.builder().setExcluded("jvm").build());
    verify(builder)
        .setInternalMetricsUnstableMetricsFilter(
            IncludeExclude.builder().setIncluded("tomcat.*", "kafka.*").build());
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void legacyTarget(boolean declarative) {
    JmxTelemetryBuilder builder = mock(JmxTelemetryBuilder.class);
    JmxMetricInsightInstaller.configure(
        builder,
        config(
            declarative,
            singletonMap("otel.jmx.target.system", "tomcat,jvm"),
            "target:\n  system: [tomcat, jvm]"),
        false);

    verify(builder)
        .setInternalMetricsSystemFilter(
            IncludeExclude.builder().setIncluded("tomcat", "jvm").build());
    verify(builder).setInternalMetricsUnstableMetricsFilter(IncludeExclude.builder().build());
    logs.assertContains("'otel.jmx.target.system' is deprecated and will be removed in 3.0.");
    logs.assertContains("Use 'otel.jmx.metrics.experimental.included'");
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void legacyExperimentalPrefix(boolean declarative) {
    JmxTelemetryBuilder builder = mock(JmxTelemetryBuilder.class);
    JmxMetricInsightInstaller.configure(
        builder,
        config(
            declarative,
            singletonMap("otel.jmx.target.system", "experimental-tomcat"),
            "target:\n  system: [experimental-tomcat]"),
        false);

    verify(builder)
        .setInternalMetricsSystemFilter(IncludeExclude.builder().setIncluded("tomcat").build());
    verify(builder).setInternalMetricsUnstableMetricsFilter(IncludeExclude.builder().build());
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void emptyExperimentalInclusionRetainsLegacyTarget(boolean declarative) {
    Map<String, String> properties = new HashMap<>();
    properties.put("otel.jmx.target.system", "tomcat");
    properties.put("otel.jmx.metrics.experimental.included", "");
    JmxTelemetryBuilder builder = mock(JmxTelemetryBuilder.class);
    JmxMetricInsightInstaller.configure(
        builder,
        config(
            declarative,
            properties,
            "target:\n  system: [tomcat]\nmetrics:\n  experimental:\n    included: []"),
        false);

    verify(builder)
        .setInternalMetricsSystemFilter(IncludeExclude.builder().setIncluded("tomcat").build());
    verify(builder).setInternalMetricsUnstableMetricsFilter(IncludeExclude.builder().build());
    logs.assertContains("'otel.jmx.target.system' is deprecated");
  }

  @ParameterizedTest
  @MethodSource("modes")
  void experimentalInclusionTakesPrecedence(boolean declarative, boolean v3Preview) {
    Map<String, String> properties = new HashMap<>();
    properties.put("otel.jmx.target.system", "kafka-broker");
    properties.put("otel.jmx.metrics.experimental.included", "tomcat.*");
    JmxTelemetryBuilder builder = mock(JmxTelemetryBuilder.class);
    JmxMetricInsightInstaller.configure(
        builder,
        config(
            declarative,
            properties,
            "target:\n  system: [kafka-broker]\nmetrics:\n  experimental:\n    included: [tomcat.*]"),
        v3Preview);

    verify(builder)
        .setInternalMetricsSystemFilter(IncludeExclude.builder().setExcluded("jvm").build());
    verify(builder)
        .setInternalMetricsUnstableMetricsFilter(
            IncludeExclude.builder().setIncluded("tomcat.*").build());
    logs.assertDoesNotContain("is deprecated");
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void previewIgnoresLegacyTarget(boolean declarative) {
    JmxTelemetryBuilder builder = mock(JmxTelemetryBuilder.class);
    JmxMetricInsightInstaller.configure(
        builder,
        config(
            declarative,
            singletonMap("otel.jmx.target.system", "tomcat"),
            "target:\n  system: [tomcat]"),
        true);

    verify(builder)
        .setInternalMetricsSystemFilter(IncludeExclude.builder().setExcluded("jvm").build());
    verify(builder)
        .setInternalMetricsUnstableMetricsFilter(IncludeExclude.builder().setExcluded("*").build());
    logs.assertDoesNotContain("is deprecated");
  }

  @ParameterizedTest
  @MethodSource("modes")
  void normalFilters(boolean declarative, boolean v3Preview) {
    Map<String, String> properties = new HashMap<>();
    properties.put("otel.jmx.metrics.included", "tomcat.*");
    properties.put("otel.jmx.metrics.excluded", "tomcat.test.unstable");
    properties.put("otel.jmx.target.system", "tomcat");
    JmxTelemetryBuilder builder = mock(JmxTelemetryBuilder.class);
    JmxMetricInsightInstaller.configure(
        builder,
        config(
            declarative,
            properties,
            "target:\n  system: [tomcat]\nmetrics:\n  included: [tomcat.*]\n"
                + "  excluded: [tomcat.test.unstable]"),
        v3Preview);

    verify(builder)
        .setMetrics(
            IncludeExclude.builder()
                .setIncluded("tomcat.*")
                .setExcluded("tomcat.test.unstable")
                .build());
  }

  @ParameterizedTest
  @MethodSource("modes")
  void collectsStableMetricsByDefault(boolean declarative, boolean v3Preview) {
    JmxTelemetryBuilder builder = JmxTelemetry.builder(testing.getOpenTelemetry());
    JmxMetricInsightInstaller.configure(builder, config(declarative, emptyMap(), "{}"), v3Preview);
    cleanup.deferCleanup(builder.build().start());

    testing.waitAndAssertMetrics(
        "io.opentelemetry.jmx", metric -> metric.hasName("tomcat.test.stable"));
  }

  @ParameterizedTest
  @MethodSource("modes")
  void collectsOptedInMetricsWithoutLegacyTarget(boolean declarative, boolean v3Preview) {
    JmxTelemetryBuilder builder = JmxTelemetry.builder(testing.getOpenTelemetry());
    JmxMetricInsightInstaller.configure(
        builder,
        config(
            declarative,
            singletonMap("otel.jmx.metrics.experimental.included", "tomcat.*"),
            "metrics:\n  experimental:\n    included: [tomcat.*]"),
        v3Preview);
    cleanup.deferCleanup(builder.build().start());

    testing.waitAndAssertMetrics(
        "io.opentelemetry.jmx",
        metric -> metric.hasName("tomcat.test.stable"),
        metric -> metric.hasName("tomcat.test.unstable"));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void collectsLegacyStableAndUnstableMetrics(boolean declarative) {
    JmxTelemetryBuilder builder = JmxTelemetry.builder(testing.getOpenTelemetry());
    JmxMetricInsightInstaller.configure(
        builder,
        config(
            declarative,
            singletonMap("otel.jmx.target.system", "tomcat"),
            "target:\n  system: [tomcat]"),
        false);
    cleanup.deferCleanup(builder.build().start());

    testing.waitAndAssertMetrics(
        "io.opentelemetry.jmx",
        metric -> metric.hasName("tomcat.test.stable"),
        metric -> metric.hasName("tomcat.test.unstable"));
  }

  @ParameterizedTest
  @MethodSource("modes")
  void excludesOptedInMetrics(boolean declarative, boolean v3Preview) {
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
                + "  included: [tomcat.*]\n  excluded: [tomcat.test.unstable]"),
        v3Preview);
    cleanup.deferCleanup(builder.build().start());

    testing.waitAndAssertMetrics(
        "io.opentelemetry.jmx", metric -> metric.hasName("tomcat.test.stable"));
  }

  @ParameterizedTest
  @MethodSource("modes")
  void customFileDoesNotRequireExperimentalInclusion(
      boolean declarative, boolean v3Preview, @TempDir Path directory) throws IOException {
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
            "config: ['" + rules + "']"),
        v3Preview);
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
  @MethodSource("modes")
  void customFileUsesNormalExclusions(
      boolean declarative, boolean v3Preview, @TempDir Path directory) throws IOException {
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
            declarative, properties, "config: ['" + rules + "']\nmetrics:\n  excluded: [custom.*]"),
        v3Preview);
    cleanup.deferCleanup(builder.build().start());

    testing.waitAndAssertMetrics(
        "io.opentelemetry.jmx", metric -> metric.hasName("tomcat.test.stable"));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void unsupportedLegacyTargetWarns(boolean declarative) {
    JmxTelemetryBuilder builder = mock(JmxTelemetryBuilder.class);
    JmxMetricInsightInstaller.configure(
        builder,
        config(
            declarative,
            singletonMap("otel.jmx.target.system", "unknown"),
            "target:\n  system: [unknown]"),
        false);

    logs.assertContains("JMX target system unknown is not supported.");
    verify(builder, never())
        .setInternalMetricsSystemFilter(IncludeExclude.builder().setExcluded("jvm").build());
  }

  private static Stream<Arguments> modes() {
    return Stream.of(
        argumentSet("2.x flat", false, false),
        argumentSet("2.x YAML", true, false),
        argumentSet("v3-preview flat", false, true),
        argumentSet("v3-preview YAML", true, true));
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
