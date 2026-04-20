/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_40.incubator.metrics;

import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics.ApplicationLongCounter;

final class ApplicationLongCounter140Incubator extends ApplicationLongCounter
    implements application.io.opentelemetry.api.incubator.metrics.ExtendedLongCounter {

  ApplicationLongCounter140Incubator(LongCounter agentCounter) {
    super(agentCounter);
  }
}
