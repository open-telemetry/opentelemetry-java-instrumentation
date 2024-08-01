/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_38.incubator.metrics;

import application.io.opentelemetry.api.metrics.DoubleGaugeBuilder;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_37.incubator.metrics.BaseApplicationMeter137;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_38.metrics.ApplicationDoubleGaugeBuilder138;

class ApplicationMeter138Incubator extends BaseApplicationMeter137 {

  private final io.opentelemetry.api.metrics.Meter agentMeter;

  ApplicationMeter138Incubator(io.opentelemetry.api.metrics.Meter agentMeter) {
    super(agentMeter);
    this.agentMeter = agentMeter;
  }

  @Override
  public DoubleGaugeBuilder gaugeBuilder(String name) {
    io.opentelemetry.api.metrics.DoubleGaugeBuilder builder = agentMeter.gaugeBuilder(name);
    if (builder instanceof io.opentelemetry.api.incubator.metrics.ExtendedDoubleGaugeBuilder) {
      return new ApplicationDoubleGaugeBuilder138Incubator(builder);
    }
    return new ApplicationDoubleGaugeBuilder138(builder);
  }
}
