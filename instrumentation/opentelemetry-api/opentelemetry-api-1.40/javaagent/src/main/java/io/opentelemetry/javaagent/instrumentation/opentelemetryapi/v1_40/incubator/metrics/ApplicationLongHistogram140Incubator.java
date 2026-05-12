/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_40.incubator.metrics;

import io.opentelemetry.api.metrics.LongHistogram;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics.ApplicationLongHistogram;

final class ApplicationLongHistogram140Incubator extends ApplicationLongHistogram
    implements application.io.opentelemetry.api.incubator.metrics.ExtendedLongHistogram {

  ApplicationLongHistogram140Incubator(LongHistogram agentHistogram) {
    super(agentHistogram);
  }
}
