/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.metrics;

import application.io.opentelemetry.common.Labels;
import application.io.opentelemetry.metrics.LongSumObserver;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.LabelBridging;
import io.opentelemetry.metrics.AsynchronousInstrument;

class ApplicationLongSumObserver implements LongSumObserver {

  private final io.opentelemetry.metrics.LongSumObserver agentLongSumObserver;

  protected ApplicationLongSumObserver(
      io.opentelemetry.metrics.LongSumObserver agentLongSumObserver) {
    this.agentLongSumObserver = agentLongSumObserver;
  }

  @Override
  public void setCallback(Callback<LongResult> metricUpdater) {
    agentLongSumObserver.setCallback(new AgentResultLongSumObserver(metricUpdater));
  }

  static class AgentResultLongSumObserver
      implements AsynchronousInstrument.Callback<
          io.opentelemetry.metrics.LongSumObserver.LongResult> {

    private final Callback<LongResult> metricUpdater;

    protected AgentResultLongSumObserver(Callback<LongResult> metricUpdater) {
      this.metricUpdater = metricUpdater;
    }

    @Override
    public void update(io.opentelemetry.metrics.LongSumObserver.LongResult result) {
      metricUpdater.update(new ApplicationResultLongSumObserver(result));
    }
  }

  static class ApplicationResultLongSumObserver implements LongResult {

    private final io.opentelemetry.metrics.LongSumObserver.LongResult agentResultLongSumObserver;

    public ApplicationResultLongSumObserver(
        io.opentelemetry.metrics.LongSumObserver.LongResult agentResultLongSumObserver) {
      this.agentResultLongSumObserver = agentResultLongSumObserver;
    }

    @Override
    public void observe(long value, Labels labels) {
      agentResultLongSumObserver.observe(value, LabelBridging.toAgent(labels));
    }
  }

  static class Builder implements LongSumObserver.Builder {

    private final io.opentelemetry.metrics.LongSumObserver.Builder agentBuilder;

    protected Builder(io.opentelemetry.metrics.LongSumObserver.Builder agentBuilder) {
      this.agentBuilder = agentBuilder;
    }

    @Override
    public LongSumObserver.Builder setDescription(String description) {
      agentBuilder.setDescription(description);
      return this;
    }

    @Override
    public LongSumObserver.Builder setUnit(String unit) {
      agentBuilder.setUnit(unit);
      return this;
    }

    @Override
    public LongSumObserver build() {
      return new ApplicationLongSumObserver(agentBuilder.build());
    }
  }
}
