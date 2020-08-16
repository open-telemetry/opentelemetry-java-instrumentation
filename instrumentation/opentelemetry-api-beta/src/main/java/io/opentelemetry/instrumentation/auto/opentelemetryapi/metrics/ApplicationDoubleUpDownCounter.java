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
import application.io.opentelemetry.metrics.DoubleUpDownCounter;
import io.opentelemetry.instrumentation.auto.opentelemetryapi.LabelBridging;

class ApplicationDoubleUpDownCounter implements DoubleUpDownCounter {

  private final io.opentelemetry.metrics.DoubleUpDownCounter agentDoubleUpDownCounter;

  ApplicationDoubleUpDownCounter(
      io.opentelemetry.metrics.DoubleUpDownCounter agentDoubleUpDownCounter) {
    this.agentDoubleUpDownCounter = agentDoubleUpDownCounter;
  }

  io.opentelemetry.metrics.DoubleUpDownCounter getAgentDoubleUpDownCounter() {
    return agentDoubleUpDownCounter;
  }

  @Override
  public void add(double delta, Labels labels) {
    agentDoubleUpDownCounter.add(delta, LabelBridging.toAgent(labels));
  }

  @Override
  public BoundDoubleUpDownCounter bind(Labels labels) {
    return new BoundInstrument(agentDoubleUpDownCounter.bind(LabelBridging.toAgent(labels)));
  }

  static class BoundInstrument implements BoundDoubleUpDownCounter {

    private final io.opentelemetry.metrics.DoubleUpDownCounter.BoundDoubleUpDownCounter
        agentBoundDoubleUpDownCounter;

    BoundInstrument(
        io.opentelemetry.metrics.DoubleUpDownCounter.BoundDoubleUpDownCounter
            agentBoundDoubleUpDownCounter) {
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

  static class Builder implements DoubleUpDownCounter.Builder {

    private final io.opentelemetry.metrics.DoubleUpDownCounter.Builder agentBuilder;

    Builder(io.opentelemetry.metrics.DoubleUpDownCounter.Builder agentBuilder) {
      this.agentBuilder = agentBuilder;
    }

    @Override
    public DoubleUpDownCounter.Builder setDescription(String description) {
      agentBuilder.setDescription(description);
      return this;
    }

    @Override
    public DoubleUpDownCounter.Builder setUnit(String unit) {
      agentBuilder.setUnit(unit);
      return this;
    }

    @Override
    public DoubleUpDownCounter.Builder setConstantLabels(Labels constantLabels) {
      agentBuilder.setConstantLabels(LabelBridging.toAgent(constantLabels));
      return this;
    }

    @Override
    public DoubleUpDownCounter build() {
      return new ApplicationDoubleUpDownCounter(agentBuilder.build());
    }
  }
}
