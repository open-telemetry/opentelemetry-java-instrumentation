/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_38.incubator.metrics;

import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_37.incubator.metrics.BaseApplicationMeter137;

class ApplicationMeter138Incubator extends BaseApplicationMeter137 {

  private final Meter agentMeter;

  ApplicationMeter138Incubator(Meter agentMeter) {
    super(agentMeter);
    this.agentMeter = agentMeter;
  }

  @Override
  public application.io.opentelemetry.api.metrics.DoubleGaugeBuilder gaugeBuilder(String name) {
    return new ApplicationDoubleGaugeBuilder138Incubator(agentMeter.gaugeBuilder(name));
  }
}
