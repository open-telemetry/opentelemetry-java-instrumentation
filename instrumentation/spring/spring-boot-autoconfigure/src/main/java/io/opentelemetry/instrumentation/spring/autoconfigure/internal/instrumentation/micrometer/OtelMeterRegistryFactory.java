/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.micrometer;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.micrometer.v1_5.OpenTelemetryMeterRegistry;
import io.opentelemetry.instrumentation.micrometer.v1_5.OpenTelemetryMeterRegistryBuilder;
import io.opentelemetry.instrumentation.micrometer.v1_5.internal.Internal;
import org.springframework.beans.factory.ListableBeanFactory;

final class OtelMeterRegistryFactory {

  static MeterRegistry create(
      OpenTelemetry openTelemetry, Clock micrometerClock, ListableBeanFactory beanFactory) {
    OpenTelemetryMeterRegistryBuilder builder =
        OpenTelemetryMeterRegistry.builder(openTelemetry).setClock(micrometerClock);
    Internal.setMetersHiddenFromSearch(builder, hasOtherMeterRegistry(beanFactory));
    return builder.build();
  }

  /**
   * Returns whether a meter registry other than the OpenTelemetry one is configured, which means
   * that the OpenTelemetry registry ends up in a composite registry next to it.
   *
   * <p>All bean definitions are registered before any of them is instantiated, so this sees the
   * fallback {@code SimpleMeterRegistry} even though it is contributed by an auto-configuration
   * that this one is ordered after.
   */
  private static boolean hasOtherMeterRegistry(ListableBeanFactory beanFactory) {
    // the OpenTelemetry registry itself is always one of them
    return beanFactory.getBeanNamesForType(MeterRegistry.class, false, false).length > 1;
  }

  private OtelMeterRegistryFactory() {}
}
