/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.opentelemetryapi.metrics;

import application.io.opentelemetry.common.Labels;
import application.io.opentelemetry.metrics.DoubleUpDownSumObserver;
import io.opentelemetry.instrumentation.auto.opentelemetryapi.LabelBridging;
import io.opentelemetry.metrics.AsynchronousInstrument;

class ApplicationDoubleUpDownSumObserver implements DoubleUpDownSumObserver {

  private final io.opentelemetry.metrics.DoubleUpDownSumObserver agentDoubleUpDownSumObserver;

  protected ApplicationDoubleUpDownSumObserver(
      io.opentelemetry.metrics.DoubleUpDownSumObserver agentDoubleUpDownSumObserver) {
    this.agentDoubleUpDownSumObserver = agentDoubleUpDownSumObserver;
  }

  @Override
  public void setCallback(Callback<DoubleResult> metricUpdater) {
    agentDoubleUpDownSumObserver.setCallback(new AgentResultDoubleUpDownSumObserver(metricUpdater));
  }

  static class AgentResultDoubleUpDownSumObserver
      implements AsynchronousInstrument.Callback<
          io.opentelemetry.metrics.DoubleUpDownSumObserver.DoubleResult> {

    private final Callback<DoubleResult> metricUpdater;

    protected AgentResultDoubleUpDownSumObserver(Callback<DoubleResult> metricUpdater) {
      this.metricUpdater = metricUpdater;
    }

    @Override
    public void update(io.opentelemetry.metrics.DoubleUpDownSumObserver.DoubleResult result) {
      metricUpdater.update(new ApplicationResultDoubleUpDownSumObserver(result));
    }
  }

  static class ApplicationResultDoubleUpDownSumObserver implements DoubleResult {

    private final io.opentelemetry.metrics.DoubleUpDownSumObserver.DoubleResult
        agentResultDoubleUpDownSumObserver;

    public ApplicationResultDoubleUpDownSumObserver(
        io.opentelemetry.metrics.DoubleUpDownSumObserver.DoubleResult
            agentResultDoubleUpDownSumObserver) {
      this.agentResultDoubleUpDownSumObserver = agentResultDoubleUpDownSumObserver;
    }

    @Override
    public void observe(double value, Labels labels) {
      agentResultDoubleUpDownSumObserver.observe(value, LabelBridging.toAgent(labels));
    }
  }

  static class Builder implements DoubleUpDownSumObserver.Builder {

    private final io.opentelemetry.metrics.DoubleUpDownSumObserver.Builder agentBuilder;

    protected Builder(io.opentelemetry.metrics.DoubleUpDownSumObserver.Builder agentBuilder) {
      this.agentBuilder = agentBuilder;
    }

    @Override
    public DoubleUpDownSumObserver.Builder setDescription(String description) {
      agentBuilder.setDescription(description);
      return this;
    }

    @Override
    public DoubleUpDownSumObserver.Builder setUnit(String unit) {
      agentBuilder.setUnit(unit);
      return this;
    }

    @Override
    public DoubleUpDownSumObserver build() {
      return new ApplicationDoubleUpDownSumObserver(agentBuilder.build());
    }
  }
}
