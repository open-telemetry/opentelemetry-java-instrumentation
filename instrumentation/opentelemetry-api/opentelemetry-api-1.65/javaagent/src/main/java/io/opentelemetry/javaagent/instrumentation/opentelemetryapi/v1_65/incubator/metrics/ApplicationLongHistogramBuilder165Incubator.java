/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_65.incubator.metrics;

import io.opentelemetry.api.metrics.LongHistogramBuilder;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_37.incubator.metrics.ApplicationLongHistogramBuilder137;

// extends the 1.37 incubator builder directly instead of the 1.40 incubator builder: the 1.40
// incubator builder's build() method constructs the 1.40 incubator marker instrument class,
// which does not implement ExtendedLongHistogram#bind(Attributes) (added in 1.65) and would
// otherwise make this class fail muzzle validation
final class ApplicationLongHistogramBuilder165Incubator extends ApplicationLongHistogramBuilder137 {

  private final LongHistogramBuilder agentBuilder;

  ApplicationLongHistogramBuilder165Incubator(LongHistogramBuilder agentBuilder) {
    super(agentBuilder);
    this.agentBuilder = agentBuilder;
  }

  @Override
  public application.io.opentelemetry.api.metrics.LongHistogram build() {
    return new ApplicationLongHistogram165Incubator(agentBuilder.build());
  }
}
