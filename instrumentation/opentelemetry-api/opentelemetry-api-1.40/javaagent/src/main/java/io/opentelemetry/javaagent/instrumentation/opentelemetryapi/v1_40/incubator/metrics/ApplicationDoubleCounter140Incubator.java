/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_40.incubator.metrics;

import io.opentelemetry.api.metrics.DoubleCounter;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics.ApplicationDoubleCounter;

final class ApplicationDoubleCounter140Incubator extends ApplicationDoubleCounter
    implements application.io.opentelemetry.api.incubator.metrics.ExtendedDoubleCounter {

  ApplicationDoubleCounter140Incubator(DoubleCounter agentCounter) {
    super(agentCounter);
  }
}
