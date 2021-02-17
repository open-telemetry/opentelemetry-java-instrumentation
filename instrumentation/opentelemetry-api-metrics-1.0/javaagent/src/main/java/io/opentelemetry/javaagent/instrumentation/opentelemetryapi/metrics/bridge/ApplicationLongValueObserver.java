/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.metrics.bridge;

import application.io.opentelemetry.api.metrics.LongValueObserver;
import application.io.opentelemetry.api.metrics.LongValueObserverBuilder;
import application.io.opentelemetry.api.metrics.common.Labels;
import java.util.function.Consumer;

class ApplicationLongValueObserver implements LongValueObserver {

  private final io.opentelemetry.api.metrics.LongValueObserver agentLongValueObserver;

  public ApplicationLongValueObserver(
      io.opentelemetry.api.metrics.LongValueObserver agentLongValueObserver) {
    this.agentLongValueObserver = agentLongValueObserver;
  }

  public static class AgentResultLongValueObserver
      implements Consumer<io.opentelemetry.api.metrics.LongValueObserver.LongResult> {

    private final Consumer<LongResult> metricUpdater;

    public AgentResultLongValueObserver(Consumer<LongResult> metricUpdater) {
      this.metricUpdater = metricUpdater;
    }

    @Override
    public void accept(io.opentelemetry.api.metrics.LongValueObserver.LongResult result) {
      metricUpdater.accept(new ApplicationResultLongValueObserver(result));
    }
  }

  public static class ApplicationResultLongValueObserver implements LongResult {

    private final io.opentelemetry.api.metrics.LongValueObserver.LongResult
        agentResultLongValueObserver;

    public ApplicationResultLongValueObserver(
        io.opentelemetry.api.metrics.LongValueObserver.LongResult agentResultLongValueObserver) {
      this.agentResultLongValueObserver = agentResultLongValueObserver;
    }

    @Override
    public void observe(long value, Labels labels) {
      agentResultLongValueObserver.observe(value, LabelBridging.toAgent(labels));
    }
  }

  static class Builder implements LongValueObserverBuilder {

    private final io.opentelemetry.api.metrics.LongValueObserverBuilder agentBuilder;

    public Builder(io.opentelemetry.api.metrics.LongValueObserverBuilder agentBuilder) {
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
    public LongValueObserver build() {
      return new ApplicationLongValueObserver(agentBuilder.build());
    }
  }
}
