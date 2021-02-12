/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.metrics.bridge;

import application.io.opentelemetry.api.metrics.LongUpDownSumObserver;
import application.io.opentelemetry.api.metrics.LongUpDownSumObserverBuilder;
import application.io.opentelemetry.api.metrics.common.Labels;
import java.util.function.Consumer;

class ApplicationLongUpDownSumObserver implements LongUpDownSumObserver {

  private final io.opentelemetry.api.metrics.LongUpDownSumObserver agentLongUpDownSumObserver;

  protected ApplicationLongUpDownSumObserver(
      io.opentelemetry.api.metrics.LongUpDownSumObserver agentLongUpDownSumObserver) {
    this.agentLongUpDownSumObserver = agentLongUpDownSumObserver;
  }

  static class AgentResultLongUpDownSumObserver
      implements Consumer<io.opentelemetry.api.metrics.LongUpDownSumObserver.LongResult> {

    private final Consumer<LongResult> metricUpdater;

    protected AgentResultLongUpDownSumObserver(Consumer<LongResult> metricUpdater) {
      this.metricUpdater = metricUpdater;
    }

    @Override
    public void accept(io.opentelemetry.api.metrics.LongUpDownSumObserver.LongResult result) {
      metricUpdater.accept(new ApplicationResultLongUpDownSumObserver(result));
    }
  }

  static class ApplicationResultLongUpDownSumObserver implements LongResult {

    private final io.opentelemetry.api.metrics.LongUpDownSumObserver.LongResult
        agentResultLongUpDownSumObserver;

    public ApplicationResultLongUpDownSumObserver(
        io.opentelemetry.api.metrics.LongUpDownSumObserver.LongResult
            agentResultLongUpDownSumObserver) {
      this.agentResultLongUpDownSumObserver = agentResultLongUpDownSumObserver;
    }

    @Override
    public void observe(long value, Labels labels) {
      agentResultLongUpDownSumObserver.observe(value, LabelBridging.toAgent(labels));
    }
  }

  static class Builder implements LongUpDownSumObserverBuilder {

    private final io.opentelemetry.api.metrics.LongUpDownSumObserverBuilder agentBuilder;

    protected Builder(io.opentelemetry.api.metrics.LongUpDownSumObserverBuilder agentBuilder) {
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
    public Builder setUpdater(Consumer<LongResult> callback) {
      agentBuilder.setUpdater(
          result ->
              callback.accept((sum, labels) -> result.observe(sum, LabelBridging.toAgent(labels))));
      return this;
    }

    @Override
    public LongUpDownSumObserver build() {
      return new ApplicationLongUpDownSumObserver(agentBuilder.build());
    }
  }
}
