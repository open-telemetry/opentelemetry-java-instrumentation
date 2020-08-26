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
import application.io.opentelemetry.metrics.LongCounter;
import io.opentelemetry.instrumentation.auto.opentelemetryapi.LabelBridging;

class ApplicationLongCounter implements LongCounter {

  private final io.opentelemetry.metrics.LongCounter agentLongCounter;

  ApplicationLongCounter(io.opentelemetry.metrics.LongCounter agentLongCounter) {
    this.agentLongCounter = agentLongCounter;
  }

  io.opentelemetry.metrics.LongCounter getAgentLongCounter() {
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

    private final io.opentelemetry.metrics.LongCounter.BoundLongCounter agentBoundLongCounter;

    BoundInstrument(io.opentelemetry.metrics.LongCounter.BoundLongCounter agentBoundLongCounter) {
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

    private final io.opentelemetry.metrics.LongCounter.Builder agentBuilder;

    Builder(io.opentelemetry.metrics.LongCounter.Builder agentBuilder) {
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
    public LongCounter.Builder setConstantLabels(Labels constantLabels) {
      agentBuilder.setConstantLabels(LabelBridging.toAgent(constantLabels));
      return this;
    }

    @Override
    public LongCounter build() {
      return new ApplicationLongCounter(agentBuilder.build());
    }
  }
}
