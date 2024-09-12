/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_37.incubator.metrics;

import application.io.opentelemetry.api.metrics.DoubleGaugeBuilder;

class ApplicationMeter137 extends BaseApplicationMeter137 {

  private final io.opentelemetry.api.metrics.Meter agentMeter;

  protected ApplicationMeter137(io.opentelemetry.api.metrics.Meter agentMeter) {
    super(agentMeter);
    this.agentMeter = agentMeter;
  }

  @Override
  public DoubleGaugeBuilder gaugeBuilder(String name) {
    return new ApplicationDoubleGaugeBuilder137(agentMeter.gaugeBuilder(name));
  }
}
