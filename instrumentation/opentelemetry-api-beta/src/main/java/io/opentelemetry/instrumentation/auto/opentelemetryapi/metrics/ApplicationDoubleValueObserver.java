/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.opentelemetryapi.metrics;

import application.io.opentelemetry.common.Labels;
import application.io.opentelemetry.metrics.DoubleValueObserver;
import io.opentelemetry.instrumentation.auto.opentelemetryapi.LabelBridging;
import io.opentelemetry.metrics.AsynchronousInstrument;

class ApplicationDoubleValueObserver implements DoubleValueObserver {

  private final io.opentelemetry.metrics.DoubleValueObserver agentDoubleValueObserver;

  protected ApplicationDoubleValueObserver(
      io.opentelemetry.metrics.DoubleValueObserver agentDoubleValueObserver) {
    this.agentDoubleValueObserver = agentDoubleValueObserver;
  }

  @Override
  public void setCallback(Callback<DoubleResult> metricUpdater) {
    agentDoubleValueObserver.setCallback(new AgentResultDoubleValueObserver(metricUpdater));
  }

  static class AgentResultDoubleValueObserver
      implements AsynchronousInstrument.Callback<
          io.opentelemetry.metrics.DoubleValueObserver.DoubleResult> {

    private final Callback<DoubleResult> metricUpdater;

    protected AgentResultDoubleValueObserver(Callback<DoubleResult> metricUpdater) {
      this.metricUpdater = metricUpdater;
    }

    @Override
    public void update(io.opentelemetry.metrics.DoubleValueObserver.DoubleResult result) {
      metricUpdater.update(new ApplicationResultDoubleValueObserver(result));
    }
  }

  static class ApplicationResultDoubleValueObserver implements DoubleResult {

    private final io.opentelemetry.metrics.DoubleValueObserver.DoubleResult
        agentResultDoubleValueObserver;

    public ApplicationResultDoubleValueObserver(
        io.opentelemetry.metrics.DoubleValueObserver.DoubleResult agentResultDoubleValueObserver) {
      this.agentResultDoubleValueObserver = agentResultDoubleValueObserver;
    }

    @Override
    public void observe(double value, Labels labels) {
      agentResultDoubleValueObserver.observe(value, LabelBridging.toAgent(labels));
    }
  }

  static class Builder implements DoubleValueObserver.Builder {

    private final io.opentelemetry.metrics.DoubleValueObserver.Builder agentBuilder;

    protected Builder(io.opentelemetry.metrics.DoubleValueObserver.Builder agentBuilder) {
      this.agentBuilder = agentBuilder;
    }

    @Override
    public DoubleValueObserver.Builder setDescription(String description) {
      agentBuilder.setDescription(description);
      return this;
    }

    @Override
    public DoubleValueObserver.Builder setUnit(String unit) {
      agentBuilder.setUnit(unit);
      return this;
    }

    @Override
    public DoubleValueObserver build() {
      return new ApplicationDoubleValueObserver(agentBuilder.build());
    }
  }
}
