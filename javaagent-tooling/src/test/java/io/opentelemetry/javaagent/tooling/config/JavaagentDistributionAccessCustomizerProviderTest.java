/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.config;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.logging.Level.WARNING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.javaagent.extension.instrumentation.internal.AgentDistributionConfig;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.DeclarativeConfiguration;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.DeclarativeConfigurationCustomizer;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.model.OpenTelemetryConfigurationModel;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

class JavaagentDistributionAccessCustomizerProviderTest {

  private static final Logger logger =
      Logger.getLogger(JavaagentDistributionAccessCustomizerProvider.class.getName());

  @BeforeEach
  @AfterEach
  void resetDistributionConfig() {
    AgentDistributionConfig.resetForTest();
  }

  @ParameterizedTest
  @MethodSource("selectorResolutionArguments")
  void resolvesSelectors(
      List<String> enabled,
      List<String> disabled,
      boolean v3Preview,
      List<String> names,
      boolean defaultEnabled,
      boolean expected) {
    applyConfig(enabled, disabled, v3Preview);

    assertThat(AgentDistributionConfig.get().isInstrumentationEnabled(names, defaultEnabled))
        .isEqualTo(expected);
  }

  private static Stream<Arguments> selectorResolutionArguments() {
    return Stream.of(
        argumentSet(
            "canonical enabled",
            singletonList("reactor_3_1"),
            emptyList(),
            false,
            singletonList("reactor-3.1"),
            false,
            true),
        argumentSet(
            "canonical disabled",
            emptyList(),
            singletonList("reactor_3_1"),
            false,
            singletonList("reactor-3.1"),
            true,
            false),
        argumentSet(
            "dotted enabled fallback",
            singletonList("reactor_3.1"),
            emptyList(),
            false,
            singletonList("reactor-3.1"),
            false,
            true),
        argumentSet(
            "v3 preview ignores dotted enabled selector",
            singletonList("reactor_3.1"),
            emptyList(),
            true,
            singletonList("reactor-3.1"),
            false,
            false),
        argumentSet(
            "v3 preview ignores dotted disabled selector",
            emptyList(),
            singletonList("reactor_3.1"),
            true,
            singletonList("reactor-3.1"),
            true,
            true),
        argumentSet(
            "canonical enabled takes precedence over dotted disabled",
            singletonList("reactor_3_1"),
            singletonList("reactor_3.1"),
            false,
            singletonList("reactor-3.1"),
            false,
            true),
        argumentSet(
            "canonical disabled takes precedence over dotted enabled",
            singletonList("reactor_3.1"),
            singletonList("reactor_3_1"),
            false,
            singletonList("reactor-3.1"),
            true,
            false),
        argumentSet(
            "disabled takes precedence for canonical spelling",
            singletonList("reactor_3_1"),
            singletonList("reactor_3_1"),
            false,
            singletonList("reactor-3.1"),
            true,
            false),
        argumentSet(
            "disabled takes precedence for dotted spelling",
            singletonList("reactor_3.1"),
            singletonList("reactor_3.1"),
            false,
            singletonList("reactor-3.1"),
            true,
            false),
        argumentSet(
            "first module alias wins when disabled",
            singletonList("reactor_3_1"),
            singletonList("reactor"),
            false,
            asList("reactor", "reactor-3.1"),
            true,
            false),
        argumentSet(
            "first module alias wins when enabled",
            singletonList("reactor_3_1"),
            singletonList("reactor"),
            false,
            asList("reactor-3.1", "reactor"),
            false,
            true),
        argumentSet(
            "configured hyphens are not normalized",
            singletonList("reactor-3_1"),
            emptyList(),
            false,
            singletonList("reactor-3.1"),
            false,
            false),
        argumentSet(
            "default applies without matching selector",
            singletonList("other"),
            emptyList(),
            false,
            singletonList("reactor-3.1"),
            true,
            true));
  }

  @ParameterizedTest
  @CsvSource({"reactor-3.1, false", "reactor-3.1, true", "reactor-3_1, false", "reactor-3_1, true"})
  void hyphenatedSelectorsDoNotMatchOrWarn(String selector, boolean v3Preview) {
    TestHandler handler = new TestHandler();
    logger.addHandler(handler);
    try {
      applyConfig(singletonList(selector), singletonList(selector), v3Preview);

      AgentDistributionConfig config = AgentDistributionConfig.get();
      assertThat(config.isInstrumentationEnabled("reactor-3.1", false)).isFalse();
      assertThat(config.isInstrumentationEnabled("reactor-3.1", true)).isTrue();
      assertThat(handler.records).isEmpty();
    } finally {
      logger.removeHandler(handler);
    }
  }

  @Test
  void warnsOncePerDeprecatedEntryAndList() {
    TestHandler handler = new TestHandler();
    logger.addHandler(handler);
    try {
      applyConfig(
          asList("reactor_3.1", "reactor_3.1", "other_1.2", "reactor_3_1"),
          asList("reactor_3.1", "reactor_3.1"),
          false);

      assertThat(handler.records).hasSize(3).allMatch(record -> record.getLevel() == WARNING);
      assertThat(handler.records)
          .filteredOn(
              record ->
                  record.getMessage().contains("distribution.javaagent.instrumentation.enabled"))
          .extracting(LogRecord::getMessage)
          .containsExactlyInAnyOrder(
              "Declarative configuration entry 'reactor_3.1' in"
                  + " 'distribution.javaagent.instrumentation.enabled' is deprecated; use"
                  + " 'reactor_3_1' instead. The deprecated entry will be removed in 3.0.",
              "Declarative configuration entry 'other_1.2' in"
                  + " 'distribution.javaagent.instrumentation.enabled' is deprecated; use"
                  + " 'other_1_2' instead. The deprecated entry will be removed in 3.0.");
      assertThat(handler.records)
          .filteredOn(
              record ->
                  record.getMessage().contains("distribution.javaagent.instrumentation.disabled"))
          .extracting(LogRecord::getMessage)
          .containsExactly(
              "Declarative configuration entry 'reactor_3.1' in"
                  + " 'distribution.javaagent.instrumentation.disabled' is deprecated; use"
                  + " 'reactor_3_1' instead. The deprecated entry will be removed in 3.0.");

      AgentDistributionConfig config = AgentDistributionConfig.get();
      assertThat(config.isInstrumentationEnabled("reactor-3.1", false)).isTrue();
      assertThat(config.isInstrumentationEnabled("reactor-3.1", false)).isTrue();
      assertThat(handler.records).hasSize(3);
    } finally {
      logger.removeHandler(handler);
    }
  }

  @Test
  void v3PreviewIgnoresDeprecatedEntriesWithoutWarnings() {
    TestHandler handler = new TestHandler();
    logger.addHandler(handler);
    try {
      applyConfig(singletonList("reactor_3.1"), singletonList("other_1.2"), true);

      assertThat(handler.records).isEmpty();
    } finally {
      logger.removeHandler(handler);
    }
  }

  private static void applyConfig(List<String> enabled, List<String> disabled, boolean v3Preview) {
    StringBuilder yaml = new StringBuilder("file_format: \"1.1\"\n");
    if (v3Preview) {
      yaml.append(
          "instrumentation/development:\n"
              + "  java:\n"
              + "    common:\n"
              + "      v3_preview: true\n");
    }
    yaml.append(
        "distribution:\n" + "  javaagent:\n" + "    instrumentation:\n" + "      enabled:\n");
    for (String selector : enabled) {
      yaml.append("        - ").append(selector).append('\n');
    }
    yaml.append("      disabled:\n");
    for (String selector : disabled) {
      yaml.append("        - ").append(selector).append('\n');
    }

    OpenTelemetryConfigurationModel model =
        DeclarativeConfiguration.parse(new ByteArrayInputStream(yaml.toString().getBytes(UTF_8)));
    List<Function<OpenTelemetryConfigurationModel, OpenTelemetryConfigurationModel>> customizers =
        new ArrayList<>();
    new JavaagentDistributionAccessCustomizerProvider()
        .customize(new ModelCustomizerCollector(customizers));
    for (Function<OpenTelemetryConfigurationModel, OpenTelemetryConfigurationModel> customizer :
        customizers) {
      model = customizer.apply(model);
    }
  }

  private static final class ModelCustomizerCollector
      implements DeclarativeConfigurationCustomizer {
    private final List<Function<OpenTelemetryConfigurationModel, OpenTelemetryConfigurationModel>>
        customizers;

    private ModelCustomizerCollector(
        List<Function<OpenTelemetryConfigurationModel, OpenTelemetryConfigurationModel>>
            customizers) {
      this.customizers = customizers;
    }

    @Override
    public void addModelCustomizer(
        Function<OpenTelemetryConfigurationModel, OpenTelemetryConfigurationModel> customizer) {
      customizers.add(customizer);
    }

    @Override
    public <T extends SpanExporter> void addSpanExporterCustomizer(
        Class<T> exporterType, BiFunction<T, DeclarativeConfigProperties, T> customizer) {}

    @Override
    public <T extends MetricExporter> void addMetricExporterCustomizer(
        Class<T> exporterType, BiFunction<T, DeclarativeConfigProperties, T> customizer) {}

    @Override
    public <T extends LogRecordExporter> void addLogRecordExporterCustomizer(
        Class<T> exporterType, BiFunction<T, DeclarativeConfigProperties, T> customizer) {}
  }

  private static final class TestHandler extends Handler {
    private final List<LogRecord> records = new ArrayList<>();

    @Override
    public void publish(LogRecord record) {
      records.add(record);
    }

    @Override
    public void flush() {}

    @Override
    public void close() {}
  }
}
