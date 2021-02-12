/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.metrics.bridge;

import application.io.opentelemetry.api.metrics.DoubleValueObserver;
import application.io.opentelemetry.api.metrics.DoubleValueObserverBuilder;
import application.io.opentelemetry.api.metrics.common.Labels;
import java.util.function.Consumer;

class ApplicationDoubleValueObserver implements DoubleValueObserver {

  private final io.opentelemetry.api.metrics.DoubleValueObserver agentDoubleValueObserver;

  protected ApplicationDoubleValueObserver(
      io.opentelemetry.api.metrics.DoubleValueObserver agentDoubleValueObserver) {
    this.agentDoubleValueObserver = agentDoubleValueObserver;
  }

  static class AgentResultDoubleValueObserver
      implements Consumer<io.opentelemetry.api.metrics.DoubleValueObserver.DoubleResult> {

    private final Consumer<DoubleResult> metricUpdater;

    protected AgentResultDoubleValueObserver(Consumer<DoubleResult> metricUpdater) {
      this.metricUpdater = metricUpdater;
    }

    @Override
    public void accept(io.opentelemetry.api.metrics.DoubleValueObserver.DoubleResult result) {
      metricUpdater.accept(new ApplicationResultDoubleValueObserver(result));
    }
  }

  static class ApplicationResultDoubleValueObserver implements DoubleResult {

    private final io.opentelemetry.api.metrics.DoubleValueObserver.DoubleResult
        agentResultDoubleValueObserver;

    public ApplicationResultDoubleValueObserver(
        io.opentelemetry.api.metrics.DoubleValueObserver.DoubleResult
            agentResultDoubleValueObserver) {
      this.agentResultDoubleValueObserver = agentResultDoubleValueObserver;
    }

    @Override
    public void observe(double value, Labels labels) {
      agentResultDoubleValueObserver.observe(value, LabelBridging.toAgent(labels));
    }
  }

  static class Builder implements DoubleValueObserverBuilder {

    private final io.opentelemetry.api.metrics.DoubleValueObserverBuilder agentBuilder;

    protected Builder(io.opentelemetry.api.metrics.DoubleValueObserverBuilder agentBuilder) {
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
    public DoubleValueObserver build() {
      return new ApplicationDoubleValueObserver(agentBuilder.build());
    }
  }
}
