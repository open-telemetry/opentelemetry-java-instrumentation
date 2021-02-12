/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.metrics.bridge;

import application.io.opentelemetry.api.metrics.DoubleSumObserver;
import application.io.opentelemetry.api.metrics.DoubleSumObserverBuilder;
import application.io.opentelemetry.api.metrics.common.Labels;
import java.util.function.Consumer;

class ApplicationDoubleSumObserver implements DoubleSumObserver {

  private final io.opentelemetry.api.metrics.DoubleSumObserver agentDoubleSumObserver;

  protected ApplicationDoubleSumObserver(
      io.opentelemetry.api.metrics.DoubleSumObserver agentDoubleSumObserver) {
    this.agentDoubleSumObserver = agentDoubleSumObserver;
  }

  static class AgentResultDoubleSumObserver
      implements Consumer<io.opentelemetry.api.metrics.DoubleSumObserver.DoubleResult> {

    private final Consumer<DoubleResult> metricUpdater;

    protected AgentResultDoubleSumObserver(Consumer<DoubleResult> metricUpdater) {
      this.metricUpdater = metricUpdater;
    }

    @Override
    public void accept(io.opentelemetry.api.metrics.DoubleSumObserver.DoubleResult result) {
      metricUpdater.accept(new ApplicationResultDoubleSumObserver(result));
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

  static class Builder implements DoubleSumObserverBuilder {

    private final io.opentelemetry.api.metrics.DoubleSumObserverBuilder agentBuilder;

    protected Builder(io.opentelemetry.api.metrics.DoubleSumObserverBuilder agentBuilder) {
      this.agentBuilder = agentBuilder;
    }

    @Override
    public Builder setDescription(String description) {
      agentBuilder.setDescription(description);
      return this;
    }

    @Override
    public Builder setUnit(String unit) {
      agentBuilder.setUnit(unit);
      return this;
    }

    @Override
    public Builder setUpdater(Consumer<DoubleResult> callback) {
      agentBuilder.setUpdater(
          result ->
              callback.accept((sum, labels) -> result.observe(sum, LabelBridging.toAgent(labels))));
      return this;
    }

    @Override
    public DoubleSumObserver build() {
      return new ApplicationDoubleSumObserver(agentBuilder.build());
    }
  }
}
