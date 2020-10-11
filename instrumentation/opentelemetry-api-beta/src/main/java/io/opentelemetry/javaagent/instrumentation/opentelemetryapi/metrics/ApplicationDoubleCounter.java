/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.metrics;

import application.io.opentelemetry.common.Labels;
import application.io.opentelemetry.metrics.DoubleCounter;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.LabelBridging;

class ApplicationDoubleCounter implements DoubleCounter {

  private final io.opentelemetry.metrics.DoubleCounter agentDoubleCounter;

  ApplicationDoubleCounter(io.opentelemetry.metrics.DoubleCounter agentDoubleCounter) {
    this.agentDoubleCounter = agentDoubleCounter;
  }

  io.opentelemetry.metrics.DoubleCounter getAgentDoubleCounter() {
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

  static class BoundInstrument implements DoubleCounter.BoundDoubleCounter {

    private final io.opentelemetry.metrics.DoubleCounter.BoundDoubleCounter agentBoundDoubleCounter;

    BoundInstrument(
        io.opentelemetry.metrics.DoubleCounter.BoundDoubleCounter agentBoundDoubleCounter) {
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

  static class Builder implements DoubleCounter.Builder {

    private final io.opentelemetry.metrics.DoubleCounter.Builder agentBuilder;

    Builder(io.opentelemetry.metrics.DoubleCounter.Builder agentBuilder) {
      this.agentBuilder = agentBuilder;
    }

    @Override
    public DoubleCounter.Builder setDescription(String description) {
      agentBuilder.setDescription(description);
      return this;
    }

    @Override
    public DoubleCounter.Builder setUnit(String unit) {
      agentBuilder.setUnit(unit);
      return this;
    }

    @Override
    public DoubleCounter build() {
      return new ApplicationDoubleCounter(agentBuilder.build());
    }
  }
}
