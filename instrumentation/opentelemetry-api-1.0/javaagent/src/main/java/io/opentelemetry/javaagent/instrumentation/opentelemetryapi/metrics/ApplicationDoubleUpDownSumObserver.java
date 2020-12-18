/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.metrics;

import application.io.opentelemetry.api.common.Labels;
import application.io.opentelemetry.api.metrics.DoubleUpDownSumObserver;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.LabelBridging;
import java.util.function.Consumer;

class ApplicationDoubleUpDownSumObserver implements DoubleUpDownSumObserver {

  private final io.opentelemetry.api.metrics.DoubleUpDownSumObserver agentDoubleUpDownSumObserver;

  protected ApplicationDoubleUpDownSumObserver(
      io.opentelemetry.api.metrics.DoubleUpDownSumObserver agentDoubleUpDownSumObserver) {
    this.agentDoubleUpDownSumObserver = agentDoubleUpDownSumObserver;
  }

  static class AgentResultDoubleUpDownSumObserver
      implements Consumer<io.opentelemetry.api.metrics.DoubleUpDownSumObserver.DoubleResult> {

    private final Consumer<DoubleResult> metricUpdater;

    protected AgentResultDoubleUpDownSumObserver(Consumer<DoubleResult> metricUpdater) {
      this.metricUpdater = metricUpdater;
    }

    @Override
    public void accept(io.opentelemetry.api.metrics.DoubleUpDownSumObserver.DoubleResult result) {
      metricUpdater.accept(new ApplicationResultDoubleUpDownSumObserver(result));
    }
  }

  static class ApplicationResultDoubleUpDownSumObserver implements DoubleResult {

    private final io.opentelemetry.api.metrics.DoubleUpDownSumObserver.DoubleResult
        agentResultDoubleUpDownSumObserver;

    public ApplicationResultDoubleUpDownSumObserver(
        io.opentelemetry.api.metrics.DoubleUpDownSumObserver.DoubleResult
            agentResultDoubleUpDownSumObserver) {
      this.agentResultDoubleUpDownSumObserver = agentResultDoubleUpDownSumObserver;
    }

    @Override
    public void observe(double value, Labels labels) {
      agentResultDoubleUpDownSumObserver.observe(value, LabelBridging.toAgent(labels));
    }
  }

  static class Builder implements DoubleUpDownSumObserver.Builder {

    private final io.opentelemetry.api.metrics.DoubleUpDownSumObserver.Builder agentBuilder;

    protected Builder(io.opentelemetry.api.metrics.DoubleUpDownSumObserver.Builder agentBuilder) {
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
    public DoubleUpDownSumObserver.Builder setUpdater(Consumer<DoubleResult> callback) {
      agentBuilder.setUpdater(
          result ->
              callback.accept((sum, labels) -> result.observe(sum, LabelBridging.toAgent(labels))));
      return this;
    }

    @Override
    public DoubleUpDownSumObserver build() {
      return new ApplicationDoubleUpDownSumObserver(agentBuilder.build());
    }
  }
}
