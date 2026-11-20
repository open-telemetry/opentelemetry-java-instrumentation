/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.micrometer.v1_5;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.noop.NoopCounter;
import io.micrometer.core.instrument.noop.NoopDistributionSummary;
import io.micrometer.core.instrument.noop.NoopFunctionCounter;
import io.micrometer.core.instrument.noop.NoopFunctionTimer;
import io.micrometer.core.instrument.noop.NoopGauge;
import io.micrometer.core.instrument.noop.NoopLongTaskTimer;
import io.micrometer.core.instrument.noop.NoopMeter;
import io.micrometer.core.instrument.noop.NoopTimer;
import io.opentelemetry.instrumentation.micrometer.v1_5.internal.OpenTelemetryInstrument;

/**
 * Meters that create no OpenTelemetry instruments.
 *
 * <p>These are used instead of denying the meter with a {@code MeterFilter}: a denied meter is a
 * plain Micrometer noop meter, and when the bridge is part of a {@code CompositeMeterRegistry} the
 * application could read its zero value through the composite meter. Implementing {@link
 * OpenTelemetryInstrument} lets the agent skip these meters when reading composite meter values,
 * the same as any other bridged meter.
 */
final class SuppressedInstruments {

  static Gauge gauge(Meter.Id id) {
    return new SuppressedGauge(id);
  }

  static Counter counter(Meter.Id id) {
    return new SuppressedCounter(id);
  }

  static Timer timer(Meter.Id id) {
    return new SuppressedTimer(id);
  }

  static DistributionSummary distributionSummary(Meter.Id id) {
    return new SuppressedDistributionSummary(id);
  }

  static LongTaskTimer longTaskTimer(Meter.Id id) {
    return new SuppressedLongTaskTimer(id);
  }

  static FunctionTimer functionTimer(Meter.Id id) {
    return new SuppressedFunctionTimer(id);
  }

  static FunctionCounter functionCounter(Meter.Id id) {
    return new SuppressedFunctionCounter(id);
  }

  static Meter meter(Meter.Id id) {
    return new SuppressedMeter(id);
  }

  private static final class SuppressedGauge extends NoopGauge implements OpenTelemetryInstrument {
    SuppressedGauge(Meter.Id id) {
      super(id);
    }
  }

  private static final class SuppressedCounter extends NoopCounter
      implements OpenTelemetryInstrument {
    SuppressedCounter(Meter.Id id) {
      super(id);
    }
  }

  private static final class SuppressedTimer extends NoopTimer implements OpenTelemetryInstrument {
    SuppressedTimer(Meter.Id id) {
      super(id);
    }
  }

  private static final class SuppressedDistributionSummary extends NoopDistributionSummary
      implements OpenTelemetryInstrument {
    SuppressedDistributionSummary(Meter.Id id) {
      super(id);
    }
  }

  private static final class SuppressedLongTaskTimer extends NoopLongTaskTimer
      implements OpenTelemetryInstrument {
    SuppressedLongTaskTimer(Meter.Id id) {
      super(id);
    }
  }

  private static final class SuppressedFunctionTimer extends NoopFunctionTimer
      implements OpenTelemetryInstrument {
    SuppressedFunctionTimer(Meter.Id id) {
      super(id);
    }
  }

  private static final class SuppressedFunctionCounter extends NoopFunctionCounter
      implements OpenTelemetryInstrument {
    SuppressedFunctionCounter(Meter.Id id) {
      super(id);
    }
  }

  private static final class SuppressedMeter extends NoopMeter implements OpenTelemetryInstrument {
    SuppressedMeter(Meter.Id id) {
      super(id);
    }
  }

  private SuppressedInstruments() {}
}
