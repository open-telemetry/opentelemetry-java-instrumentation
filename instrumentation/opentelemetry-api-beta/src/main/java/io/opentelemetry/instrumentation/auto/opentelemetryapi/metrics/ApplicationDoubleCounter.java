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

package io.opentelemetry.auto.instrumentation.opentelemetryapi.metrics;

import application.io.opentelemetry.common.Labels;
import application.io.opentelemetry.metrics.DoubleCounter;
import io.opentelemetry.auto.instrumentation.opentelemetryapi.LabelBridging;

class ApplicationDoubleCounter implements DoubleCounter {

  private final io.opentelemetry.metrics.DoubleCounter agentDoubleCounter;

  ApplicationDoubleCounter(final io.opentelemetry.metrics.DoubleCounter agentDoubleCounter) {
    this.agentDoubleCounter = agentDoubleCounter;
  }

  io.opentelemetry.metrics.DoubleCounter getAgentDoubleCounter() {
    return agentDoubleCounter;
  }

  @Override
  public void add(final double delta, final Labels labels) {
    agentDoubleCounter.add(delta, LabelBridging.toAgent(labels));
  }

  @Override
  public BoundDoubleCounter bind(final Labels labels) {
    return new BoundInstrument(agentDoubleCounter.bind(LabelBridging.toAgent(labels)));
  }

  static class BoundInstrument implements DoubleCounter.BoundDoubleCounter {

    private final io.opentelemetry.metrics.DoubleCounter.BoundDoubleCounter agentBoundDoubleCounter;

    BoundInstrument(
        final io.opentelemetry.metrics.DoubleCounter.BoundDoubleCounter agentBoundDoubleCounter) {
      this.agentBoundDoubleCounter = agentBoundDoubleCounter;
    }

    @Override
    public void add(final double delta) {
      agentBoundDoubleCounter.add(delta);
    }

    @Override
    public void unbind() {
      agentBoundDoubleCounter.unbind();
    }
  }

  static class Builder implements DoubleCounter.Builder {

    private final io.opentelemetry.metrics.DoubleCounter.Builder agentBuilder;

    Builder(final io.opentelemetry.metrics.DoubleCounter.Builder agentBuilder) {
      this.agentBuilder = agentBuilder;
    }

    @Override
    public DoubleCounter.Builder setDescription(final String description) {
      agentBuilder.setDescription(description);
      return this;
    }

    @Override
    public DoubleCounter.Builder setUnit(final String unit) {
      agentBuilder.setUnit(unit);
      return this;
    }

    @Override
    public DoubleCounter.Builder setConstantLabels(final Labels constantLabels) {
      agentBuilder.setConstantLabels(LabelBridging.toAgent(constantLabels));
      return this;
    }

    @Override
    public DoubleCounter build() {
      return new ApplicationDoubleCounter(agentBuilder.build());
    }
  }
}
