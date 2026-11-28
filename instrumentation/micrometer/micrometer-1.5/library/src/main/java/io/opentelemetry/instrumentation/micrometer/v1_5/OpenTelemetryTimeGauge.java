/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.micrometer.v1_5;

import io.micrometer.core.instrument.AbstractMeter;
import io.micrometer.core.instrument.TimeGauge;
import java.util.concurrent.TimeUnit;

final class OpenTelemetryTimeGauge extends AbstractMeter implements TimeGauge, RemovableMeter {

  private final OpenTelemetryGauge<?> gauge;
  private final TimeUnit baseTimeUnit;

  OpenTelemetryTimeGauge(OpenTelemetryGauge<?> gauge, TimeUnit baseTimeUnit) {
    super(gauge.getId());
    this.gauge = gauge;
    this.baseTimeUnit = baseTimeUnit;
  }

  @Override
  public double value() {
    return gauge.value();
  }

  @Override
  public TimeUnit baseTimeUnit() {
    return baseTimeUnit;
  }

  @Override
  public void onRemove() {
    gauge.onRemove();
  }
}
