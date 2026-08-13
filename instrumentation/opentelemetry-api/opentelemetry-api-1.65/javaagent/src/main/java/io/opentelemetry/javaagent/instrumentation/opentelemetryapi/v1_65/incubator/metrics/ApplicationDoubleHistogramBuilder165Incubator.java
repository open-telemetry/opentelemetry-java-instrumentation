/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_65.incubator.metrics;

import io.opentelemetry.api.metrics.DoubleHistogramBuilder;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_37.incubator.metrics.ApplicationDoubleHistogramBuilder137;

// extends the 1.37 incubator builder directly instead of the 1.40 incubator builder: the 1.40
// incubator builder's build() method constructs the 1.40 incubator marker instrument class,
// which does not implement ExtendedDoubleHistogram#bind(Attributes) (added in 1.65) and would
// otherwise make this class fail muzzle validation
final class ApplicationDoubleHistogramBuilder165Incubator
    extends ApplicationDoubleHistogramBuilder137 {

  private final DoubleHistogramBuilder agentBuilder;

  ApplicationDoubleHistogramBuilder165Incubator(DoubleHistogramBuilder agentBuilder) {
    super(agentBuilder);
    this.agentBuilder = agentBuilder;
  }

  @Override
  public application.io.opentelemetry.api.metrics.LongHistogramBuilder ofLongs() {
    return new ApplicationLongHistogramBuilder165Incubator(agentBuilder.ofLongs());
  }

  @Override
  public application.io.opentelemetry.api.metrics.DoubleHistogram build() {
    return new ApplicationDoubleHistogram165Incubator(agentBuilder.build());
  }
}
