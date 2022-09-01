/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.micrometer1shim;

import static io.opentelemetry.instrumentation.micrometer1shim.Bridging.baseUnit;
import static io.opentelemetry.instrumentation.micrometer1shim.Bridging.name;
import static io.opentelemetry.instrumentation.micrometer1shim.Bridging.tagsAsAttributes;

import io.micrometer.core.instrument.AbstractMeter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.config.NamingConvention;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleCounter;
import io.opentelemetry.api.metrics.Meter;
import java.util.Collections;

final class OpenTelemetryCounter extends AbstractMeter implements Counter, RemovableMeter {

  // TODO: use bound instruments when they're available
  private final DoubleCounter otelCounter;
  private final Attributes attributes;

  private volatile boolean removed = false;

  OpenTelemetryCounter(Id id, NamingConvention namingConvention, Meter otelMeter) {
    super(id);

    this.attributes = tagsAsAttributes(id, namingConvention);
    String conventionName = name(id, namingConvention);
    this.otelCounter =
        otelMeter
            .counterBuilder(conventionName)
            .setDescription(Bridging.description(id))
            .setUnit(baseUnit(id))
            .ofDoubles()
            .build();
  }

  @Override
  public void increment(double v) {
    if (removed) {
      return;
    }
    otelCounter.add(v, attributes);
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
  public void onRemove() {
    removed = true;
  }
}
