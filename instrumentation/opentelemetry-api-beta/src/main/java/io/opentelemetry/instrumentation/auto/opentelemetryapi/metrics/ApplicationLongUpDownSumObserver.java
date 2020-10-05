/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.opentelemetryapi.metrics;

import application.io.opentelemetry.common.Labels;
import application.io.opentelemetry.metrics.LongUpDownSumObserver;
import io.opentelemetry.instrumentation.auto.opentelemetryapi.LabelBridging;
import io.opentelemetry.metrics.AsynchronousInstrument;

class ApplicationLongUpDownSumObserver implements LongUpDownSumObserver {

  private final io.opentelemetry.metrics.LongUpDownSumObserver agentLongUpDownSumObserver;

  protected ApplicationLongUpDownSumObserver(
      io.opentelemetry.metrics.LongUpDownSumObserver agentLongUpDownSumObserver) {
    this.agentLongUpDownSumObserver = agentLongUpDownSumObserver;
  }

  @Override
  public void setCallback(Callback<LongResult> metricUpdater) {
    agentLongUpDownSumObserver.setCallback(new AgentResultLongUpDownSumObserver(metricUpdater));
  }

  static class AgentResultLongUpDownSumObserver
      implements AsynchronousInstrument.Callback<
          io.opentelemetry.metrics.LongUpDownSumObserver.LongResult> {

    private final Callback<LongResult> metricUpdater;

    protected AgentResultLongUpDownSumObserver(Callback<LongResult> metricUpdater) {
      this.metricUpdater = metricUpdater;
    }

    @Override
    public void update(io.opentelemetry.metrics.LongUpDownSumObserver.LongResult result) {
      metricUpdater.update(new ApplicationResultLongUpDownSumObserver(result));
    }
  }

  static class ApplicationResultLongUpDownSumObserver implements LongResult {

    private final io.opentelemetry.metrics.LongUpDownSumObserver.LongResult
        agentResultLongUpDownSumObserver;

    public ApplicationResultLongUpDownSumObserver(
        io.opentelemetry.metrics.LongUpDownSumObserver.LongResult
            agentResultLongUpDownSumObserver) {
      this.agentResultLongUpDownSumObserver = agentResultLongUpDownSumObserver;
    }

    @Override
    public void observe(long value, Labels labels) {
      agentResultLongUpDownSumObserver.observe(value, LabelBridging.toAgent(labels));
    }
  }

  static class Builder implements LongUpDownSumObserver.Builder {

    private final io.opentelemetry.metrics.LongUpDownSumObserver.Builder agentBuilder;

    protected Builder(io.opentelemetry.metrics.LongUpDownSumObserver.Builder agentBuilder) {
      this.agentBuilder = agentBuilder;
    }

    @Override
    public LongUpDownSumObserver.Builder setDescription(String description) {
      agentBuilder.setDescription(description);
      return this;
    }

    @Override
    public LongUpDownSumObserver.Builder setUnit(String unit) {
      agentBuilder.setUnit(unit);
      return this;
    }

    @Override
    public LongUpDownSumObserver build() {
      return new ApplicationLongUpDownSumObserver(agentBuilder.build());
    }
  }
}
