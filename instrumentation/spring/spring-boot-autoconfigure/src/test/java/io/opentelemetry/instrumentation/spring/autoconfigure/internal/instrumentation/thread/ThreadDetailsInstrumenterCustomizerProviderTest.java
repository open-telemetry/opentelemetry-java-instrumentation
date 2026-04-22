/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.thread;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.opentelemetry.instrumentation.api.incubator.instrumenter.InstrumenterCustomizer;
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
  void configureDeclarativeConfigUsesDistributionProperty() {
    MockEnvironment environment =
        new MockEnvironment()
            .withProperty("otel.distribution.spring_starter.thread_details_enabled", "true");
    ThreadDetailsInstrumenterCustomizerProvider.configureDeclarativeConfig(environment);

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
