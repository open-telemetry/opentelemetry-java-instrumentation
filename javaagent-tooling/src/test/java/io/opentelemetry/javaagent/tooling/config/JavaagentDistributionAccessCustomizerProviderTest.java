/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.config;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
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
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class JavaagentDistributionAccessCustomizerProviderTest {

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
      List<String> names,
      boolean defaultEnabled,
      boolean expected) {
    applyConfig(enabled, disabled);

    assertThat(AgentDistributionConfig.get().isInstrumentationEnabled(names, defaultEnabled))
        .isEqualTo(expected);
  }

  private static Stream<Arguments> selectorResolutionArguments() {
    return Stream.of(
        argumentSet(
            "canonical enabled",
            singletonList("reactor_3_1"),
            emptyList(),
            singletonList("reactor-3.1"),
            false,
            true),
        argumentSet(
            "canonical disabled",
            emptyList(),
            singletonList("reactor_3_1"),
            singletonList("reactor-3.1"),
            true,
            false),
        argumentSet(
            "disabled takes precedence",
            singletonList("reactor_3_1"),
            singletonList("reactor_3_1"),
            singletonList("reactor-3.1"),
            true,
            false),
        argumentSet(
            "first module alias wins when disabled",
            singletonList("reactor_3_1"),
            singletonList("reactor"),
            asList("reactor", "reactor-3.1"),
            true,
            false),
        argumentSet(
            "first module alias wins when enabled",
            singletonList("reactor_3_1"),
            singletonList("reactor"),
            asList("reactor-3.1", "reactor"),
            false,
            true),
        argumentSet(
            "configured hyphens are not normalized",
            singletonList("reactor-3_1"),
            emptyList(),
            singletonList("reactor-3.1"),
            false,
            false),
        argumentSet(
            "default applies without matching selector",
            singletonList("other"),
            emptyList(),
            singletonList("reactor-3.1"),
            true,
            true));
  }

  private static void applyConfig(List<String> enabled, List<String> disabled) {
    StringBuilder yaml = new StringBuilder("file_format: \"1.1\"\n");
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
}
