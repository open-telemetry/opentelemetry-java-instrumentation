/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.resources;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.sdk.extension.incubator.fileconfig.DeclarativeConfigurationCustomizer;
import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.OpenTelemetryConfigurationModel;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class ResourceCustomizerProviderTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void customize() {
    new ResourceCustomizerProvider()
        .customize(
            new DeclarativeConfigurationCustomizer() {
              @Override
              public void addModelCustomizer(
                  Function<OpenTelemetryConfigurationModel, OpenTelemetryConfigurationModel>
                      customizer) {
                OpenTelemetryConfigurationModel configurationModel =
                    customizer.apply(new OpenTelemetryConfigurationModel());

                try {
                  assertThat(objectMapper.writeValueAsString(configurationModel.getResource()))
                      .isEqualTo(
                          "{\"attributes\":[],\"detection/development\":{\"detectors\":[{\"opentelemetry_javaagent_distribution\":null}]}}");
                } catch (JsonProcessingException e) {
                  throw new AssertionError(e);
                }
              }

              @Override
              public <T extends SpanExporter> void addSpanExporterCustomizer(
                  Class<T> exporterType,
                  BiFunction<T, DeclarativeConfigProperties, T> customizer) {}

              @Override
              public <T extends MetricExporter> void addMetricExporterCustomizer(
                  Class<T> exporterType,
                  BiFunction<T, DeclarativeConfigProperties, T> customizer) {}

              @Override
              public <T extends LogRecordExporter> void addLogRecordExporterCustomizer(
                  Class<T> exporterType,
                  BiFunction<T, DeclarativeConfigProperties, T> customizer) {}
            });
  }
}
