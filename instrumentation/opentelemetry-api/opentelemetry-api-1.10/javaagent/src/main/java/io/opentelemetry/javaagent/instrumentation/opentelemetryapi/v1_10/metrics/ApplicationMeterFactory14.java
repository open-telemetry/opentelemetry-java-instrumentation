/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics;

import io.opentelemetry.api.metrics.Meter;

public final class ApplicationMeterFactory14 implements ApplicationMeterFactory {
  @Override
  public ApplicationMeter newMeter(Meter agentMeter) {
    return new ApplicationMeter(agentMeter);
  }
}
