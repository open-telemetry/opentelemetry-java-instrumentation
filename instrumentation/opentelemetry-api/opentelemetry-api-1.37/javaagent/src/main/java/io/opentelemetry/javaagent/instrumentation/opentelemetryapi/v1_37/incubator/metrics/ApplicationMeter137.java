/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_37.incubator.metrics;

import application.io.opentelemetry.api.metrics.DoubleGaugeBuilder;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics.ApplicationDoubleGaugeBuilder;

class ApplicationMeter137 extends BaseApplicationMeter137 {

  private final io.opentelemetry.api.metrics.Meter agentMeter;

  protected ApplicationMeter137(io.opentelemetry.api.metrics.Meter agentMeter) {
    super(agentMeter);
    this.agentMeter = agentMeter;
  }

  @Override
  public DoubleGaugeBuilder gaugeBuilder(String name) {
    io.opentelemetry.api.metrics.DoubleGaugeBuilder builder = agentMeter.gaugeBuilder(name);
    if (builder instanceof io.opentelemetry.api.incubator.metrics.ExtendedDoubleGaugeBuilder) {
      return new ApplicationDoubleGaugeBuilder137(builder);
    }
    return new ApplicationDoubleGaugeBuilder(builder);
  }
}
