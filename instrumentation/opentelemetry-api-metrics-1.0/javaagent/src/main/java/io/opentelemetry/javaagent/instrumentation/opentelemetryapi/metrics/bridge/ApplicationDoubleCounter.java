/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.metrics.bridge;

import application.io.opentelemetry.api.metrics.BoundDoubleCounter;
import application.io.opentelemetry.api.metrics.DoubleCounter;
import application.io.opentelemetry.api.metrics.DoubleCounterBuilder;
import application.io.opentelemetry.api.metrics.common.Labels;

class ApplicationDoubleCounter implements DoubleCounter {

  private final io.opentelemetry.api.metrics.DoubleCounter agentDoubleCounter;

  ApplicationDoubleCounter(io.opentelemetry.api.metrics.DoubleCounter agentDoubleCounter) {
    this.agentDoubleCounter = agentDoubleCounter;
  }

  io.opentelemetry.api.metrics.DoubleCounter getAgentDoubleCounter() {
    return agentDoubleCounter;
  }

  @Override
  public void add(double delta, Labels labels) {
    agentDoubleCounter.add(delta, LabelBridging.toAgent(labels));
  }

  @Override
  public void add(double v) {
    agentDoubleCounter.add(v);
  }

  @Override
  public BoundDoubleCounter bind(Labels labels) {
    return new BoundInstrument(agentDoubleCounter.bind(LabelBridging.toAgent(labels)));
  }

  static class BoundInstrument implements BoundDoubleCounter {

    private final io.opentelemetry.api.metrics.BoundDoubleCounter agentBoundDoubleCounter;

    BoundInstrument(io.opentelemetry.api.metrics.BoundDoubleCounter agentBoundDoubleCounter) {
      this.agentBoundDoubleCounter = agentBoundDoubleCounter;
    }

    @Override
    public void add(double delta) {
      agentBoundDoubleCounter.add(delta);
    }

    @Override
    public void unbind() {
      agentBoundDoubleCounter.unbind();
    }
  }

  static class Builder implements DoubleCounterBuilder {

    private final io.opentelemetry.api.metrics.DoubleCounterBuilder agentBuilder;

    Builder(io.opentelemetry.api.metrics.DoubleCounterBuilder agentBuilder) {
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
    public DoubleCounter build() {
      return new ApplicationDoubleCounter(agentBuilder.build());
    }
  }
}
