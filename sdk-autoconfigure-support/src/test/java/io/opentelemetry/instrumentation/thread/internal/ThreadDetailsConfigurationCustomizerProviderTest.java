/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.thread.internal;

import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.extension.incubator.fileconfig.DeclarativeConfiguration;
import io.opentelemetry.sdk.extension.incubator.fileconfig.DeclarativeConfigurationBuilder;
import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.ExperimentalLanguageSpecificInstrumentationModel;
import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.InstrumentationModel;
import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.OpenTelemetryConfigurationModel;
import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.TracerProviderModel;
import org.junit.jupiter.api.Test;

class ThreadDetailsConfigurationCustomizerProviderTest {

  static final String PROCESSOR = "AddThreadDetailsSpanProcessor";

  @Test
  void disabledByDefault() {
    OpenTelemetryConfigurationModel model = modelWithTracer();

    try (OpenTelemetrySdk sdk = DeclarativeConfiguration.create(model)) {
      assertThat(sdk.toString()).doesNotContain(PROCESSOR);
    }
  }

  @Test
  void enabled() {
    OpenTelemetryConfigurationModel model =
        modelWithTracer()
            .withInstrumentationDevelopment(
                new InstrumentationModel()
                    .withJava(
                        new ExperimentalLanguageSpecificInstrumentationModel()
                            .withAdditionalProperty(
                                "thread_details", singletonMap("enabled", true))));

    try (OpenTelemetrySdk sdk = DeclarativeConfiguration.create(model)) {
      assertThat(sdk.toString()).containsOnlyOnce(PROCESSOR);
    }
  }

  private static OpenTelemetryConfigurationModel modelWithTracer() {
    return new DeclarativeConfigurationBuilder()
        .customizeModel(
            new OpenTelemetryConfigurationModel()
                .withFileFormat("1.0-rc.1")
                .withTracerProvider(new TracerProviderModel()));
  }
}
