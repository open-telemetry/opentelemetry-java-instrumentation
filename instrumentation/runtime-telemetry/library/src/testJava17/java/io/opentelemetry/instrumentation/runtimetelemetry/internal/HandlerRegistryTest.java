/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimetelemetry.internal;

import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.instrumentation.runtimetelemetry.internal.garbagecollection.G1GarbageCollectionHandler;
import io.opentelemetry.instrumentation.runtimetelemetry.internal.garbagecollection.GarbageCollectionHandler;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class HandlerRegistryTest {

  @Test
  void doesNotCreateInstrumentsForUnavailableEvents() {
    Meter meter = mock(Meter.class);

    assertThat(
            HandlerRegistry.getHandlers(meter, "jvm.class.count"::equals, false, false, emptySet()))
        .isEmpty();
    verifyNoInteractions(meter);
  }

  @Test
  void doesNotFallBackWhenCollectorSpecificEventsExist() {
    Meter meter = SdkMeterProvider.builder().build().get("test");

    assertThat(
            HandlerRegistry.getHandlers(
                meter,
                "jvm.gc.duration"::equals,
                false,
                false,
                Set.of("jdk.G1GarbageCollection", "jdk.GarbageCollection"),
                List.of("G1 Young Generation", "G1 Old Generation")))
        .singleElement()
        .isInstanceOf(G1GarbageCollectionHandler.class);
  }

  @Test
  void fallsBackToGenericGarbageCollectionEvent() {
    Meter meter = SdkMeterProvider.builder().build().get("test");

    assertThat(
            HandlerRegistry.getHandlers(
                meter,
                "jvm.gc.duration"::equals,
                false,
                false,
                singleton("jdk.GarbageCollection"),
                List.of("young generation scavenger", "complete scavenger")))
        .singleElement()
        .isInstanceOf(GarbageCollectionHandler.class);
  }
}
