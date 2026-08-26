/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.micrometer.v1_5.internal;

import io.opentelemetry.instrumentation.micrometer.v1_5.OpenTelemetryMeterRegistryBuilder;
import java.util.function.BiConsumer;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class Internal {

  @Nullable
  private static volatile BiConsumer<OpenTelemetryMeterRegistryBuilder, Boolean>
      setMetersHiddenFromSearch;

  /**
   * Hides the registered meters from the meter registry search APIs, i.e. {@code getMeters()},
   * {@code find(String)} and {@code get(String)}. Meter registration, {@code
   * forEachMeter(Consumer)} and meter removal are not affected.
   *
   * <p>The OpenTelemetry meter registry only forwards metrics to OpenTelemetry; it cannot read
   * metric values back, so the meters it returns always measure to nothing. Readers that pick a
   * single registry out of a {@code CompositeMeterRegistry}, such as Spring Boot Actuator's metrics
   * endpoint, otherwise have no way of telling that they should read from a different member of the
   * composite instead.
   *
   * <p>Only set this when the registry is part of a composite that has another member capable of
   * reading metric values, otherwise the metrics become invisible to those readers entirely.
   *
   * <p>This is disabled by default, set this to {@code true} to hide the registered meters.
   */
  public static void setMetersHiddenFromSearch(
      OpenTelemetryMeterRegistryBuilder builder, boolean metersHiddenFromSearch) {
    if (setMetersHiddenFromSearch != null) {
      setMetersHiddenFromSearch.accept(builder, metersHiddenFromSearch);
    }
  }

  public static void internalSetMetersHiddenFromSearch(
      BiConsumer<OpenTelemetryMeterRegistryBuilder, Boolean> setMetersHiddenFromSearch) {
    Internal.setMetersHiddenFromSearch = setMetersHiddenFromSearch;
  }

  private Internal() {}
}
