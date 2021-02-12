/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.metrics.bridge;

import application.io.opentelemetry.api.metrics.LongSumObserver;
import application.io.opentelemetry.api.metrics.LongSumObserverBuilder;
import application.io.opentelemetry.api.metrics.common.Labels;
import java.util.function.Consumer;

class ApplicationLongSumObserver implements LongSumObserver {

  private final io.opentelemetry.api.metrics.LongSumObserver agentLongSumObserver;

  protected ApplicationLongSumObserver(
      io.opentelemetry.api.metrics.LongSumObserver agentLongSumObserver) {
    this.agentLongSumObserver = agentLongSumObserver;
  }

  static class AgentResultLongSumObserver
      implements Consumer<io.opentelemetry.api.metrics.LongSumObserver.LongResult> {

    private final Consumer<LongResult> metricUpdater;

    protected AgentResultLongSumObserver(Consumer<LongResult> metricUpdater) {
      this.metricUpdater = metricUpdater;
    }

    @Override
    public void accept(io.opentelemetry.api.metrics.LongSumObserver.LongResult result) {
      metricUpdater.accept(new ApplicationResultLongSumObserver(result));
    }
  }

  static class ApplicationResultLongSumObserver implements LongResult {

    private final io.opentelemetry.api.metrics.LongSumObserver.LongResult
        agentResultLongSumObserver;

    public ApplicationResultLongSumObserver(
        io.opentelemetry.api.metrics.LongSumObserver.LongResult agentResultLongSumObserver) {
      this.agentResultLongSumObserver = agentResultLongSumObserver;
    }

    @Override
    public void observe(long value, Labels labels) {
      agentResultLongSumObserver.observe(value, LabelBridging.toAgent(labels));
    }
  }

  static class Builder implements LongSumObserverBuilder {

    private final io.opentelemetry.api.metrics.LongSumObserverBuilder agentBuilder;

    protected Builder(io.opentelemetry.api.metrics.LongSumObserverBuilder agentBuilder) {
      this.agentBuilder = agentBuilder;
    }

    @Override
    public LongSumObserverBuilder setDescription(String description) {
      agentBuilder.setDescription(description);
      return this;
    }

    @Override
    public LongSumObserverBuilder setUnit(String unit) {
      agentBuilder.setUnit(unit);
      return this;
    }

    @Override
    public LongSumObserverBuilder setUpdater(Consumer<LongResult> callback) {
      agentBuilder.setUpdater(
          result ->
              callback.accept((sum, labels) -> result.observe(sum, LabelBridging.toAgent(labels))));
      return this;
    }

    @Override
    public LongSumObserver build() {
      return new ApplicationLongSumObserver(agentBuilder.build());
    }
  }
}
