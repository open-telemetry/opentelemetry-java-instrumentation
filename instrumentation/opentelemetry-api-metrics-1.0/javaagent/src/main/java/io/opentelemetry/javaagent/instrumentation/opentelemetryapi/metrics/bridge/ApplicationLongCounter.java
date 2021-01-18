/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.metrics.bridge;

import application.io.opentelemetry.api.common.Labels;
import application.io.opentelemetry.api.metrics.LongCounter;

class ApplicationLongCounter implements LongCounter {

  private final io.opentelemetry.api.metrics.LongCounter agentLongCounter;

  ApplicationLongCounter(io.opentelemetry.api.metrics.LongCounter agentLongCounter) {
    this.agentLongCounter = agentLongCounter;
  }

  io.opentelemetry.api.metrics.LongCounter getAgentLongCounter() {
    return agentLongCounter;
  }

  @Override
  public void add(long delta, Labels labels) {
    agentLongCounter.add(delta, LabelBridging.toAgent(labels));
  }

  @Override
  public void add(long l) {
    agentLongCounter.add(l);
  }

  @Override
  public BoundLongCounter bind(Labels labels) {
    return new BoundInstrument(agentLongCounter.bind(LabelBridging.toAgent(labels)));
  }

  static class BoundInstrument implements LongCounter.BoundLongCounter {

    private final io.opentelemetry.api.metrics.LongCounter.BoundLongCounter agentBoundLongCounter;

    BoundInstrument(
        io.opentelemetry.api.metrics.LongCounter.BoundLongCounter agentBoundLongCounter) {
      this.agentBoundLongCounter = agentBoundLongCounter;
    }

    @Override
    public void add(long delta) {
      agentBoundLongCounter.add(delta);
    }

    @Override
    public void unbind() {
      agentBoundLongCounter.unbind();
    }
  }

  static class Builder implements LongCounter.Builder {

    private final io.opentelemetry.api.metrics.LongCounter.Builder agentBuilder;

    Builder(io.opentelemetry.api.metrics.LongCounter.Builder agentBuilder) {
      this.agentBuilder = agentBuilder;
    }

    @Override
    public LongCounter.Builder setDescription(String description) {
      agentBuilder.setDescription(description);
      return this;
    }

    @Override
    public LongCounter.Builder setUnit(String unit) {
      agentBuilder.setUnit(unit);
      return this;
    }

    @Override
    public LongCounter build() {
      return new ApplicationLongCounter(agentBuilder.build());
    }
  }
}
