/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.logging;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.sdk.extension.incubator.fileconfig.DeclarativeConfiguration;
import io.opentelemetry.sdk.extension.incubator.fileconfig.DeclarativeConfigurationCustomizer;
import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.OpenTelemetryConfigurationModel;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SpanLoggingCustomizerProviderTest {

  @ParameterizedTest
  @CsvSource({
    "true, false, true",
    "false, false, false",
    ", false, false", // empty value means property is not set
    "invalid, false, false",
    "true, true, true",
  })
  @SuppressWarnings("StringConcatToTextBlock") // latest dep allows text blocks
  void addSpanLoggingExporter(String propertyValue, boolean alreadyAdded, boolean expected) {
    String debug =
        propertyValue == null
            ? ""
            : "instrumentation/development: \n"
                + "  java: \n"
                + "    spring_starter: \n"
                + "      debug: "
                + propertyValue;

    String yaml =
        alreadyAdded
            ? "file_format: \"1.0\"\n"
                + "tracer_provider:\n"
                + "  processors:\n"
                + "    - simple:\n"
                + "        exporter:\n"
                + "          console: {}\n"
            : "file_format: \"1.0\"\n" + debug;

    OpenTelemetryConfigurationModel model =
        applyCustomizer(
            DeclarativeConfiguration.parse(new ByteArrayInputStream(yaml.getBytes(UTF_8))),
            new DeclarativeConfigLoggingExporterAutoConfiguration.SpanLoggingCustomizerProvider());

    String console = "ConsoleExporterModel";
    if (expected) {
      assertThat(model.toString()).containsOnlyOnce(console);
    } else {
      assertThat(model.toString()).doesNotContain(console);
    }
  }

  private static OpenTelemetryConfigurationModel applyCustomizer(
      OpenTelemetryConfigurationModel model,
      DeclarativeConfigLoggingExporterAutoConfiguration.SpanLoggingCustomizerProvider provider) {
    List<Function<OpenTelemetryConfigurationModel, OpenTelemetryConfigurationModel>> customizers =
        new ArrayList<>();
    provider.customize(
        new DeclarativeConfigurationCustomizer() {
          @Override
          public void addModelCustomizer(
              Function<OpenTelemetryConfigurationModel, OpenTelemetryConfigurationModel>
                  customizer) {
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
        });
    for (Function<OpenTelemetryConfigurationModel, OpenTelemetryConfigurationModel> customizer :
        customizers) {
      model = customizer.apply(model);
    }
    return model;
  }
}
