/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.thread.internal;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AbstractThreadDetailsCustomizerProviderTest {

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void addsThreadDetailsProcessorWhenEnabled(boolean enabled) {
    OpenTelemetryConfigurationModel model =
        applyCustomizer(
            DeclarativeConfiguration.parse(
                new ByteArrayInputStream("file_format: \"1.1\"\n".getBytes(UTF_8))),
            new TestCustomizerProvider(enabled));

    assertThat(threadDetailsProcessorPresent(model)).isEqualTo(enabled);
  }

  private static boolean threadDetailsProcessorPresent(OpenTelemetryConfigurationModel model) {
    if (model.getTracerProvider() == null || model.getTracerProvider().getProcessors() == null) {
      return false;
    }
    return model.getTracerProvider().getProcessors().stream()
        .anyMatch(processor -> processor.getAdditionalProperties().containsKey("thread_details"));
  }

  private static OpenTelemetryConfigurationModel applyCustomizer(
      OpenTelemetryConfigurationModel model, AbstractThreadDetailsCustomizerProvider provider) {
    List<Function<OpenTelemetryConfigurationModel, OpenTelemetryConfigurationModel>> customizers =
        new ArrayList<>();
    provider.customize(new ModelCustomizerCollector(customizers));
    for (Function<OpenTelemetryConfigurationModel, OpenTelemetryConfigurationModel> customizer :
        customizers) {
      model = customizer.apply(model);
    }
    return model;
  }

  private static class TestCustomizerProvider extends AbstractThreadDetailsCustomizerProvider {
    private final boolean enabled;

    TestCustomizerProvider(boolean enabled) {
      this.enabled = enabled;
    }

    @Override
    protected boolean isEnabled(OpenTelemetryConfigurationModel model) {
      return enabled;
    }
  }

  private static class ModelCustomizerCollector implements DeclarativeConfigurationCustomizer {
    private final List<Function<OpenTelemetryConfigurationModel, OpenTelemetryConfigurationModel>>
        customizers;

    ModelCustomizerCollector(
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
