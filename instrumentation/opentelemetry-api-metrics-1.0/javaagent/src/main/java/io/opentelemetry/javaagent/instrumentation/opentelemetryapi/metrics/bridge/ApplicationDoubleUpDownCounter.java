/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.metrics.bridge;

import application.io.opentelemetry.api.metrics.BoundDoubleUpDownCounter;
import application.io.opentelemetry.api.metrics.DoubleUpDownCounter;
import application.io.opentelemetry.api.metrics.DoubleUpDownCounterBuilder;
import application.io.opentelemetry.api.metrics.common.Labels;

class ApplicationDoubleUpDownCounter implements DoubleUpDownCounter {

  private final io.opentelemetry.api.metrics.DoubleUpDownCounter agentDoubleUpDownCounter;

  ApplicationDoubleUpDownCounter(
      io.opentelemetry.api.metrics.DoubleUpDownCounter agentDoubleUpDownCounter) {
    this.agentDoubleUpDownCounter = agentDoubleUpDownCounter;
  }

  io.opentelemetry.api.metrics.DoubleUpDownCounter getAgentDoubleUpDownCounter() {
    return agentDoubleUpDownCounter;
  }

  @Override
  public void add(double delta, Labels labels) {
    agentDoubleUpDownCounter.add(delta, LabelBridging.toAgent(labels));
  }

  @Override
  public void add(double v) {
    agentDoubleUpDownCounter.add(v);
  }

  @Override
  public BoundDoubleUpDownCounter bind(Labels labels) {
    return new BoundInstrument(agentDoubleUpDownCounter.bind(LabelBridging.toAgent(labels)));
  }

  static class BoundInstrument implements BoundDoubleUpDownCounter {

    private final io.opentelemetry.api.metrics.BoundDoubleUpDownCounter
        agentBoundDoubleUpDownCounter;

    BoundInstrument(
        io.opentelemetry.api.metrics.BoundDoubleUpDownCounter agentBoundDoubleUpDownCounter) {
      this.agentBoundDoubleUpDownCounter = agentBoundDoubleUpDownCounter;
    }

    @Override
    public void add(double delta) {
      agentBoundDoubleUpDownCounter.add(delta);
    }

    @Override
    public void unbind() {
      agentBoundDoubleUpDownCounter.unbind();
    }
  }

  static class Builder implements DoubleUpDownCounterBuilder {

    private final io.opentelemetry.api.metrics.DoubleUpDownCounterBuilder agentBuilder;

    Builder(io.opentelemetry.api.metrics.DoubleUpDownCounterBuilder agentBuilder) {
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
    public DoubleUpDownCounter build() {
      return new ApplicationDoubleUpDownCounter(agentBuilder.build());
    }
  }
}
