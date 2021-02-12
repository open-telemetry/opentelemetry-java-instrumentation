/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.metrics.bridge;

import application.io.opentelemetry.api.metrics.BoundLongUpDownCounter;
import application.io.opentelemetry.api.metrics.LongUpDownCounter;
import application.io.opentelemetry.api.metrics.LongUpDownCounterBuilder;
import application.io.opentelemetry.api.metrics.common.Labels;

class ApplicationLongUpDownCounter implements LongUpDownCounter {

  private final io.opentelemetry.api.metrics.LongUpDownCounter agentLongUpDownCounter;

  ApplicationLongUpDownCounter(
      io.opentelemetry.api.metrics.LongUpDownCounter agentLongUpDownCounter) {
    this.agentLongUpDownCounter = agentLongUpDownCounter;
  }

  io.opentelemetry.api.metrics.LongUpDownCounter getAgentLongUpDownCounter() {
    return agentLongUpDownCounter;
  }

  @Override
  public void add(long delta, Labels labels) {
    agentLongUpDownCounter.add(delta, LabelBridging.toAgent(labels));
  }

  @Override
  public void add(long l) {
    agentLongUpDownCounter.add(l);
  }

  @Override
  public BoundLongUpDownCounter bind(Labels labels) {
    return new BoundInstrument(agentLongUpDownCounter.bind(LabelBridging.toAgent(labels)));
  }

  static class BoundInstrument implements BoundLongUpDownCounter {

    private final io.opentelemetry.api.metrics.BoundLongUpDownCounter agentBoundLongUpDownCounter;

    BoundInstrument(
        io.opentelemetry.api.metrics.BoundLongUpDownCounter agentBoundLongUpDownCounter) {
      this.agentBoundLongUpDownCounter = agentBoundLongUpDownCounter;
    }

    @Override
    public void add(long delta) {
      agentBoundLongUpDownCounter.add(delta);
    }

    @Override
    public void unbind() {
      agentBoundLongUpDownCounter.unbind();
    }
  }

  static class Builder implements LongUpDownCounterBuilder {

    private final io.opentelemetry.api.metrics.LongUpDownCounterBuilder agentBuilder;

    Builder(io.opentelemetry.api.metrics.LongUpDownCounterBuilder agentBuilder) {
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
    public LongUpDownCounter build() {
      return new ApplicationLongUpDownCounter(agentBuilder.build());
    }
  }
}
