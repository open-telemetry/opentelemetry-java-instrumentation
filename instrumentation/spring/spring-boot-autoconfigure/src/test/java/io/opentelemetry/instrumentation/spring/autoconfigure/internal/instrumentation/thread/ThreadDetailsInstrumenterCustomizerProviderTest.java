/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.thread;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.opentelemetry.instrumentation.api.incubator.instrumenter.InstrumenterCustomizer;
import io.opentelemetry.sdk.extension.incubator.fileconfig.DeclarativeConfiguration;
import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.OpenTelemetryConfigurationModel;
import io.opentelemetry.sdk.internal.SdkConfigProvider;
import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class ThreadDetailsInstrumenterCustomizerProviderTest {

  @AfterEach
  void tearDown() {
    ThreadDetailsInstrumenterCustomizerProvider.configureProperties(new MockEnvironment());
  }

  @Test
  void configurePropertiesUsesLegacyProperty() {
    MockEnvironment environment =
        new MockEnvironment()
            .withProperty("otel.instrumentation.common.thread-details.enabled", "true");
    ThreadDetailsInstrumenterCustomizerProvider.configureProperties(environment);

    InstrumenterCustomizer customizer = mock(InstrumenterCustomizer.class);
    new ThreadDetailsInstrumenterCustomizerProvider().customize(customizer);

    verify(customizer).addAttributesExtractor(any());
  }

  @Test
  @SuppressWarnings("StringConcatToTextBlock")
  void configureDeclarativeConfigReadsFromModel() {
    String yaml =
        "file_format: \"1.0\"\n"
            + "instrumentation/development:\n"
            + "  java:\n"
            + "    spring_starter:\n"
            + "      thread_details:\n"
            + "        enabled: true\n";
    OpenTelemetryConfigurationModel model =
        DeclarativeConfiguration.parse(new ByteArrayInputStream(yaml.getBytes(UTF_8)));

    ThreadDetailsInstrumenterCustomizerProvider.configureDeclarativeConfig(
        SdkConfigProvider.create(DeclarativeConfiguration.toConfigProperties(model)));

    InstrumenterCustomizer customizer = mock(InstrumenterCustomizer.class);
    new ThreadDetailsInstrumenterCustomizerProvider().customize(customizer);

    verify(customizer).addAttributesExtractor(any());
  }

  @Test
  void disabledDoesNotCustomize() {
    ThreadDetailsInstrumenterCustomizerProvider.configureProperties(new MockEnvironment());

    InstrumenterCustomizer customizer = mock(InstrumenterCustomizer.class);
    new ThreadDetailsInstrumenterCustomizerProvider().customize(customizer);

    verifyNoInteractions(customizer);
  }
}
