/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.micrometer.v1_5;

import static java.util.Collections.unmodifiableList;
import static java.util.Comparator.comparingInt;

import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.api.internal.SemconvStability;
import io.opentelemetry.instrumentation.micrometer.v1_5.OpenTelemetryMeterRegistry;
import io.opentelemetry.instrumentation.micrometer.v1_5.OpenTelemetryMeterRegistryBuilder;
import io.opentelemetry.instrumentation.micrometer.v1_5.internal.Experimental;
import io.opentelemetry.instrumentation.micrometer.v1_5.internal.OpenTelemetryInstrument;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.logging.Logger;
import javax.annotation.Nullable;

public class MicrometerSingletons {

  private static final Logger logger = Logger.getLogger(MicrometerSingletons.class.getName());

  private static final MeterRegistry meterRegistry;

  static {
    DeclarativeConfigProperties config =
        DeclarativeConfigUtil.getInstrumentationConfig(GlobalOpenTelemetry.get(), "micrometer");
    OpenTelemetryMeterRegistryBuilder builder =
        OpenTelemetryMeterRegistry.builder(GlobalOpenTelemetry.get())
            .setPrometheusMode(config.get("prometheus_mode").getBoolean("enabled", false))
            .setBaseTimeUnit(TimeUnitParser.parseConfigValue(config.getString("base_time_unit")));
    Experimental.setMicrometerHistogramGaugesEnabled(builder, getHistogramGaugesEnabled(config));
    meterRegistry = builder.build();
  }

  public static MeterRegistry meterRegistry() {
    return meterRegistry;
  }

  private static boolean getHistogramGaugesEnabled(DeclarativeConfigProperties config) {
    Boolean enabled = config.get("histogram_gauges/development").getBoolean("enabled");
    if (enabled != null) {
      return enabled;
    }

    // Support the deprecated config key until 3.0.
    if (!SemconvStability.v3Preview()) {
      Boolean deprecatedEnabled = config.get("histogram_gauges").getBoolean("enabled");
      if (deprecatedEnabled != null) {
        logger.warning(
            "The otel.instrumentation.micrometer.histogram-gauges.enabled setting or equivalent"
                + " declarative configuration is deprecated and will be removed in 3.0. Use "
                + "otel.instrumentation.micrometer.experimental.histogram-gauges.enabled or"
                + " equivalent declarative configuration instead.");
        return deprecatedEnabled;
      }
    }

    return false;
  }

  // called from CompositeMeterRegistryInstrumentation
  public static Set<MeterRegistry> sortOtelMeterRegistryLast(Set<MeterRegistry> registries) {
    // a view instead of a copy, so that it stays live and keeps the composite's identity-based
    // membership, same as the set that micrometer returns
    return new AbstractSet<MeterRegistry>() {
      @Override
      public Iterator<MeterRegistry> iterator() {
        List<MeterRegistry> sorted = new ArrayList<>(registries);
        // sort otel registry last since it doesn't support reading metric values
        // and the actuator endpoint reads metrics from the first registry
        sorted.sort(comparingInt(registry -> registry == meterRegistry ? 1 : 0));
        return unmodifiableList(sorted).iterator();
      }

      @Override
      public int size() {
        return registries.size();
      }

      @Override
      public boolean contains(Object object) {
        return registries.contains(object);
      }

      @Override
      public boolean equals(Object object) {
        return registries.equals(object);
      }

      @Override
      public int hashCode() {
        return registries.hashCode();
      }
    };
  }

  // called from code generated in AbstractCompositeMeterInstrumentation
  public static <T> Iterator<T> wrapIterator(Iterator<T> iterator) {
    if (!iterator.hasNext()) {
      return iterator;
    }

    class FilteringIterator implements Iterator<T> {
      private final Iterator<T> delegate;
      @Nullable private T next;
      private boolean hasNext;

      FilteringIterator(Iterator<T> delegate) {
        this.delegate = delegate;
        advance();
      }

      private void advance() {
        while (delegate.hasNext()) {
          T candidate = delegate.next();
          if (!(candidate instanceof OpenTelemetryInstrument)) {
            next = candidate;
            hasNext = true;
            return;
          }
        }
        next = null;
        hasNext = false;
      }

      @Override
      public boolean hasNext() {
        return hasNext;
      }

      @Override
      public T next() {
        if (!hasNext) {
          throw new NoSuchElementException();
        }
        T result = next;
        advance();
        return result;
      }
    }

    return new FilteringIterator(iterator);
  }

  private MicrometerSingletons() {}
}
