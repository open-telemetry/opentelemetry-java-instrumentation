/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimetelemetry.internal;

import static java.util.Collections.emptySet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import io.opentelemetry.api.metrics.Meter;
import org.junit.jupiter.api.Test;

class HandlerRegistryTest {

  @Test
  void doesNotCreateInstrumentsForUnavailableEvents() {
    Meter meter = mock(Meter.class);

    assertThat(
            HandlerRegistry.getHandlers(
                meter, "jvm.class.count"::equals, false, false, false, emptySet()))
        .isEmpty();
    verifyNoInteractions(meter);
  }
}
