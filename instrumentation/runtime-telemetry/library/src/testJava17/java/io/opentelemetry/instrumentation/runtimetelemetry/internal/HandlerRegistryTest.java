/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimetelemetry.internal;

import static java.util.Collections.emptySet;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.Meter;
import org.junit.jupiter.api.Test;

class HandlerRegistryTest {

  @Test
  void ignoresHandlersForUnavailableEvents() {
    Meter meter = OpenTelemetry.noop().getMeter("test");

    assertThat(
            HandlerRegistry.getHandlers(meter, "jvm.class.count"::equals, false, false, emptySet()))
        .isEmpty();
  }
}
