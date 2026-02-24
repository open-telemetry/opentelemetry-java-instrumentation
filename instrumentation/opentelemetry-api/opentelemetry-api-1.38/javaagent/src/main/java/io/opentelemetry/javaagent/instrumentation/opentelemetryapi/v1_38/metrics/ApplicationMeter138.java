/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_38.metrics;

import application.io.opentelemetry.api.metrics.DoubleGaugeBuilder;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_32.metrics.ApplicationMeter132;

public class ApplicationMeter138 extends ApplicationMeter132 {

  private final io.opentelemetry.api.metrics.Meter agentMeter;

  protected ApplicationMeter138(io.opentelemetry.api.metrics.Meter agentMeter) {
    super(agentMeter);
    this.agentMeter = agentMeter;
  }

  @Override
  public DoubleGaugeBuilder gaugeBuilder(String name) {
    return new ApplicationDoubleGaugeBuilder138(agentMeter.gaugeBuilder(name));
  }
}
