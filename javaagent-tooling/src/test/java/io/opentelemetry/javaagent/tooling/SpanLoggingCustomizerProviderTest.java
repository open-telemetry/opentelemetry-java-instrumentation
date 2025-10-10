/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.sdk.extension.incubator.fileconfig.DeclarativeConfiguration;
import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.OpenTelemetryConfigurationModel;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junitpioneer.jupiter.ClearSystemProperty;

class SpanLoggingCustomizerProviderTest {

  @ParameterizedTest
  @MethodSource("spanLoggingExporterTestData")
  @ClearSystemProperty(key = "otel.javaagent.debug")
  void addSpanLoggingExporter(String propertyValue, boolean alreadyAdded, boolean expected) {
    if (propertyValue != null) {
      System.setProperty("otel.javaagent.debug", propertyValue);
    }
    String yaml =
        alreadyAdded
            ? "file_format: \"1.0-rc.1\"\n"
                + "tracer_provider:\n"
                + "  processors:\n"
                + "    - simple:\n"
                + "        exporter:\n"
                + "          console: {}\n"
            : "file_format: \"1.0-rc.1\"\n";

    OpenTelemetryConfigurationModel model =
        applyCustomizer(
            DeclarativeConfiguration.parse(
                new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8))),
            new SpanLoggingCustomizerProvider());

    String console = "ConsoleExporterModel";
    if (expected) {
      assertThat(model.toString()).containsOnlyOnce(console);
    } else {
      assertThat(model.toString()).doesNotContain(console);
    }
  }

  private static OpenTelemetryConfigurationModel applyCustomizer(
      OpenTelemetryConfigurationModel model, SpanLoggingCustomizerProvider provider) {
    List<Function<OpenTelemetryConfigurationModel, OpenTelemetryConfigurationModel>> customizers =
        new ArrayList<>();
    provider.customize(c -> customizers.add(c));
    for (Function<OpenTelemetryConfigurationModel, OpenTelemetryConfigurationModel> customizer :
        customizers) {
      model = customizer.apply(model);
    }
    return model;
  }

  // Arguments: propertyValue, alreadyAdded, expected
  static Stream<Arguments> spanLoggingExporterTestData() {
    return Stream.of(
        Arguments.of("true", false, true),
        Arguments.of("false", false, false),
        Arguments.of(null, false, false), // null value means property is not set
        Arguments.of("invalid", false, false),
        Arguments.of("true", true, true));
  }
}
