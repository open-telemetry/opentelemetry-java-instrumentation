/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
    public LongUpDownCounter.Builder setConstantLabels(Labels constantLabels) {
      agentBuilder.setConstantLabels(LabelBridging.toAgent(constantLabels));
      return this;
    }

    @Override
    public LongUpDownCounter build() {
      return new ApplicationLongUpDownCounter(agentBuilder.build());
    }
  }
}
