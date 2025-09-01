/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.thread.internal;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.extension.incubator.fileconfig.DeclarativeConfiguration;
import io.opentelemetry.sdk.extension.incubator.fileconfig.DeclarativeConfigurationBuilder;
import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.OpenTelemetryConfigurationModel;
import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.TracerProviderModel;
import org.junit.jupiter.api.Test;

class ThreadDetailsConfigurationCustomizerProviderTest {

  @Test
  void addSpanProcessor() {
    OpenTelemetryConfigurationModel model =
        new DeclarativeConfigurationBuilder()
            .customizeModel(
                new OpenTelemetryConfigurationModel()
                    .withFileFormat("1.0-rc.1")
                    .withTracerProvider(new TracerProviderModel()));

    try (OpenTelemetrySdk sdk = DeclarativeConfiguration.create(model)) {
      assertThat(sdk.toString()).containsOnlyOnce("AddThreadDetailsSpanProcessor");
    }
  }
}
