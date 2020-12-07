/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.metrics;

import application.io.opentelemetry.api.common.Labels;
import application.io.opentelemetry.api.metrics.DoubleSumObserver;
import io.opentelemetry.api.metrics.AsynchronousInstrument;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.LabelBridging;

class ApplicationDoubleSumObserver implements DoubleSumObserver {

  private final io.opentelemetry.api.metrics.DoubleSumObserver agentDoubleSumObserver;

  protected ApplicationDoubleSumObserver(
      io.opentelemetry.api.metrics.DoubleSumObserver agentDoubleSumObserver) {
    this.agentDoubleSumObserver = agentDoubleSumObserver;
  }

  @Override
  public void setCallback(Callback<DoubleResult> metricUpdater) {
    agentDoubleSumObserver.setCallback(new AgentResultDoubleSumObserver(metricUpdater));
  }

  static class AgentResultDoubleSumObserver
      implements AsynchronousInstrument.Callback<
          io.opentelemetry.api.metrics.DoubleSumObserver.DoubleResult> {

    private final Callback<DoubleResult> metricUpdater;

    protected AgentResultDoubleSumObserver(Callback<DoubleResult> metricUpdater) {
      this.metricUpdater = metricUpdater;
    }

    @Override
    public void update(io.opentelemetry.api.metrics.DoubleSumObserver.DoubleResult result) {
      metricUpdater.update(new ApplicationResultDoubleSumObserver(result));
    }
  }

  static class ApplicationResultDoubleSumObserver implements DoubleResult {

    private final io.opentelemetry.api.metrics.DoubleSumObserver.DoubleResult
        agentResultDoubleSumObserver;

    public ApplicationResultDoubleSumObserver(
        io.opentelemetry.api.metrics.DoubleSumObserver.DoubleResult agentResultDoubleSumObserver) {
      this.agentResultDoubleSumObserver = agentResultDoubleSumObserver;
    }

    @Override
    public void observe(double value, Labels labels) {
      agentResultDoubleSumObserver.observe(value, LabelBridging.toAgent(labels));
    }
  }

  static class Builder implements DoubleSumObserver.Builder {

    private final io.opentelemetry.api.metrics.DoubleSumObserver.Builder agentBuilder;

    protected Builder(io.opentelemetry.api.metrics.DoubleSumObserver.Builder agentBuilder) {
      this.agentBuilder = agentBuilder;
    }

    @Override
    public DoubleSumObserver.Builder setDescription(String description) {
      agentBuilder.setDescription(description);
      return this;
    }

    @Override
    public DoubleSumObserver.Builder setUnit(String unit) {
      agentBuilder.setUnit(unit);
      return this;
    }

    @Override
    public DoubleSumObserver.Builder setCallback(Callback<DoubleResult> callback) {
      agentBuilder.setCallback(
          result ->
              callback.update((sum, labels) -> result.observe(sum, LabelBridging.toAgent(labels))));
      return this;
    }

    @Override
    public DoubleSumObserver build() {
      return new ApplicationDoubleSumObserver(agentBuilder.build());
    }
  }
}
