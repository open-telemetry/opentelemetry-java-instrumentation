/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.micrometer.v1_5;

import static io.opentelemetry.instrumentation.micrometer.v1_5.Bridging.*;

import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.util.MeterEquivalence;
import io.opentelemetry.instrumentation.api.internal.AsyncInstrumentRegistry;
import io.opentelemetry.instrumentation.api.internal.AsyncInstrumentRegistry.AsyncMeasurementHandle;
import java.util.Collections;
import java.util.function.ToDoubleFunction;
import javax.annotation.Nullable;

@SuppressWarnings("HashCodeToString")
final class OpenTelemetryFunctionCounter<T> implements FunctionCounter, RemovableMeter {

  private final Id id;
  private final AsyncMeasurementHandle countMeasurementHandle;

  OpenTelemetryFunctionCounter(
      Id id,
      NamingConvention namingConvention,
      T obj,
      ToDoubleFunction<T> countFunction,
      AsyncInstrumentRegistry asyncInstrumentRegistry) {
    this.id = id;

    String conventionName = name(id, namingConvention);
    countMeasurementHandle =
        asyncInstrumentRegistry.buildDoubleCounter(
            conventionName,
            description(conventionName, id),
            baseUnit(id),
            tagsAsAttributes(id, namingConvention),
            obj,
            countFunction);
  }

  @Override
  public double count() {
    UnsupportedReadLogger.logWarning();
    return Double.NaN;
  }

  @Override
  public Iterable<Measurement> measure() {
    UnsupportedReadLogger.logWarning();
    return Collections.emptyList();
  }

  @Override
  public Id getId() {
    return id;
  }

  @Override
  public void onRemove() {
    countMeasurementHandle.remove();
  }

  @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
  @Override
  public boolean equals(@Nullable Object o) {
    return MeterEquivalence.equals(this, o);
  }

  @Override
  public int hashCode() {
    return MeterEquivalence.hashCode(this);
  }
}
