/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_40.incubator.metrics;

import application.io.opentelemetry.api.metrics.LongHistogram;
import io.opentelemetry.api.metrics.LongHistogramBuilder;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_37.incubator.metrics.ApplicationLongHistogramBuilder137;

final class ApplicationLongHistogramBuilder140Incubator extends ApplicationLongHistogramBuilder137 {

  private final io.opentelemetry.api.metrics.LongHistogramBuilder agentBuilder;

  ApplicationLongHistogramBuilder140Incubator(LongHistogramBuilder agentBuilder) {
    super(agentBuilder);
    this.agentBuilder = agentBuilder;
  }

  @Override
  public LongHistogram build() {
    return new ApplicationLongHistogram140Incubator(agentBuilder.build());
  }
}
