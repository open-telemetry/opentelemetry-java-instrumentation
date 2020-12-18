/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.metrics;

import application.io.opentelemetry.api.common.Labels;
import application.io.opentelemetry.api.metrics.LongSumObserver;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.LabelBridging;
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

  static class Builder implements LongSumObserver.Builder {

    private final io.opentelemetry.api.metrics.LongSumObserver.Builder agentBuilder;

    protected Builder(io.opentelemetry.api.metrics.LongSumObserver.Builder agentBuilder) {
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
    public LongSumObserver.Builder setUpdater(Consumer<LongResult> callback) {
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
