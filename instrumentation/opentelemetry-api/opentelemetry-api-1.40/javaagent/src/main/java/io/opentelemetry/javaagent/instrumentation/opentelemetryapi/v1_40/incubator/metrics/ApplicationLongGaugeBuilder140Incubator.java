/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_40.incubator.metrics;

import application.io.opentelemetry.api.metrics.LongGauge;
import io.opentelemetry.api.metrics.LongGaugeBuilder;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_38.incubator.metrics.ApplicationLongGaugeBuilder138Incubator;

final class ApplicationLongGaugeBuilder140Incubator
    extends ApplicationLongGaugeBuilder138Incubator {

  private final io.opentelemetry.api.metrics.LongGaugeBuilder agentBuilder;

  ApplicationLongGaugeBuilder140Incubator(LongGaugeBuilder agentBuilder) {
    super(agentBuilder);
    this.agentBuilder = agentBuilder;
  }

  @Override
  public LongGauge build() {
    return new ApplicationLongGauge140Incubator(agentBuilder.build());
  }
}
