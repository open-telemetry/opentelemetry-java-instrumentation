/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_40.incubator.metrics;

import io.opentelemetry.api.metrics.LongUpDownCounter;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics.ApplicationLongUpDownCounter;

final class ApplicationLongUpDownCounter140Incubator extends ApplicationLongUpDownCounter
    implements application.io.opentelemetry.api.incubator.metrics.ExtendedLongUpDownCounter {

  ApplicationLongUpDownCounter140Incubator(LongUpDownCounter agentCounter) {
    super(agentCounter);
  }
}
