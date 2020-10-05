/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.opentelemetryapi.metrics;

import application.io.opentelemetry.common.Labels;
import application.io.opentelemetry.metrics.LongUpDownCounter;
import io.opentelemetry.instrumentation.auto.opentelemetryapi.LabelBridging;

class ApplicationLongUpDownCounter implements LongUpDownCounter {

  private final io.opentelemetry.metrics.LongUpDownCounter agentLongUpDownCounter;

  ApplicationLongUpDownCounter(io.opentelemetry.metrics.LongUpDownCounter agentLongUpDownCounter) {
    this.agentLongUpDownCounter = agentLongUpDownCounter;
  }

  io.opentelemetry.metrics.LongUpDownCounter getAgentLongUpDownCounter() {
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

    private final io.opentelemetry.metrics.LongUpDownCounter.BoundLongUpDownCounter
        agentBoundLongUpDownCounter;

    BoundInstrument(
        io.opentelemetry.metrics.LongUpDownCounter.BoundLongUpDownCounter
            agentBoundLongUpDownCounter) {
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

  static class Builder implements LongUpDownCounter.Builder {

    private final io.opentelemetry.metrics.LongUpDownCounter.Builder agentBuilder;

    Builder(io.opentelemetry.metrics.LongUpDownCounter.Builder agentBuilder) {
      this.agentBuilder = agentBuilder;
    }

    @Override
    public LongUpDownCounter.Builder setDescription(String description) {
      agentBuilder.setDescription(description);
      return this;
    }

    @Override
    public LongUpDownCounter.Builder setUnit(String unit) {
      agentBuilder.setUnit(unit);
      return this;
    }

    @Override
    public LongUpDownCounter build() {
      return new ApplicationLongUpDownCounter(agentBuilder.build());
    }
  }
}
