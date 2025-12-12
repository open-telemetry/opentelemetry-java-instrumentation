/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.micrometer.v1_5;

import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.micrometer.v1_5.OpenTelemetryMeterRegistry;
import io.opentelemetry.instrumentation.micrometer.v1_5.internal.OpenTelemetryInstrument;
import java.util.Iterator;

public final class MicrometerSingletons {

  private static final MeterRegistry METER_REGISTRY;

  static {
    METER_REGISTRY =
        OpenTelemetryMeterRegistry.builder(GlobalOpenTelemetry.get())
            .setPrometheusMode(
                DeclarativeConfigUtil.getBoolean(
                        GlobalOpenTelemetry.get(),
                        "java",
                        "micrometer",
                        "prometheus_mode",
                        "enabled")
                    .orElse(false))
            .setBaseTimeUnit(
                TimeUnitParser.parseConfigValue(
                    DeclarativeConfigUtil.getString(
                            GlobalOpenTelemetry.get(), "java", "micrometer", "base_time_unit")
                        .orElse(null)))
            .setMicrometerHistogramGaugesEnabled(
                DeclarativeConfigUtil.getBoolean(
                        GlobalOpenTelemetry.get(),
                        "java",
                        "micrometer",
                        "histogram_gauges",
                        "enabled")
                    .orElse(false))
            .build();
  }

  public static MeterRegistry meterRegistry() {
    return METER_REGISTRY;
  }

  // called from code generate in AbstractCompositeMeterInstrumentation
  public static <T> Iterator<T> wrapIterator(Iterator<T> iterator) {
    if (!iterator.hasNext()) {
      return iterator;
    }

    class FilteringIterator implements Iterator<T> {
      private final Iterator<T> delegate;
      private T next;

      FilteringIterator(Iterator<T> delegate) {
        this.delegate = delegate;
        advance();
      }

      private void advance() {
        while (delegate.hasNext()) {
          T candidate = delegate.next();
          if (!(candidate instanceof OpenTelemetryInstrument)) {
            next = candidate;
            return;
          }
        }
        next = null;
      }

      @Override
      public boolean hasNext() {
        return next != null;
      }

      @Override
      public T next() {
        T result = next;
        advance();
        return result;
      }
    }

    return new FilteringIterator(iterator);
  }

  private MicrometerSingletons() {}
}
