/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.micrometer.v1_5;

import static io.opentelemetry.javaagent.instrumentation.micrometer.v1_5.Bridging.tagsAsAttributes;

import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.util.MeterEquivalence;
import io.opentelemetry.javaagent.instrumentation.micrometer.v1_5.AsyncInstrumentRegistry.AsyncMeasurementHandle;
import java.util.Collections;
import java.util.function.ToDoubleFunction;
import javax.annotation.Nullable;

@SuppressWarnings("HashCodeToString")
final class OpenTelemetryFunctionCounter<T> implements FunctionCounter, RemovableMeter {

  private final Id id;
  private final AsyncMeasurementHandle countMeasurementHandle;

  OpenTelemetryFunctionCounter(
      Id id,
      T obj,
      ToDoubleFunction<T> countFunction,
      AsyncInstrumentRegistry asyncInstrumentRegistry) {
    this.id = id;

    countMeasurementHandle =
        asyncInstrumentRegistry.buildDoubleCounter(id, tagsAsAttributes(id), obj, countFunction);
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
