/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_31.incubator.metrics;

import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics.ApplicationMeter;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics.ApplicationMeterFactory;

// this class is used from opentelemetry-api-1.27 via reflection
public final class ApplicationMeterFactory131 implements ApplicationMeterFactory {
  @Override
  public ApplicationMeter newMeter(Meter agentMeter) {
    return new ApplicationMeter131(agentMeter);
  }
}
