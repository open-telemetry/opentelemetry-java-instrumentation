/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_40.incubator.metrics;

import io.opentelemetry.api.incubator.metrics.ExtendedLongHistogram;
import io.opentelemetry.api.metrics.LongHistogram;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics.ApplicationLongHistogram;

final class ApplicationLongHistogram140Incubator extends ApplicationLongHistogram
    implements application.io.opentelemetry.api.incubator.metrics.ExtendedLongHistogram {

  private final LongHistogram agentHistogram;

  ApplicationLongHistogram140Incubator(LongHistogram agentHistogram) {
    super(agentHistogram);
    this.agentHistogram = agentHistogram;
  }

  @Override
  public boolean isEnabled() {
    return ((ExtendedLongHistogram) agentHistogram).isEnabled();
  }
}
