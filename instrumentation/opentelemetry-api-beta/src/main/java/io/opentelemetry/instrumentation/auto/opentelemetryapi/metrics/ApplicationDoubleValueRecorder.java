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
import application.io.opentelemetry.metrics.DoubleValueRecorder;
import io.opentelemetry.instrumentation.auto.opentelemetryapi.LabelBridging;

class ApplicationDoubleValueRecorder implements DoubleValueRecorder {

  private final io.opentelemetry.metrics.DoubleValueRecorder agentDoubleValueRecorder;

  protected ApplicationDoubleValueRecorder(
      final io.opentelemetry.metrics.DoubleValueRecorder agentDoubleValueRecorder) {
    this.agentDoubleValueRecorder = agentDoubleValueRecorder;
  }

  protected io.opentelemetry.metrics.DoubleValueRecorder getAgentDoubleValueRecorder() {
    return agentDoubleValueRecorder;
  }

  @Override
  public void record(final double delta, final Labels labels) {
    agentDoubleValueRecorder.record(delta, LabelBridging.toAgent(labels));
  }

  @Override
  public BoundDoubleValueRecorder bind(final Labels labels) {
    return new BoundInstrument(agentDoubleValueRecorder.bind(LabelBridging.toAgent(labels)));
  }

  static class BoundInstrument implements DoubleValueRecorder.BoundDoubleValueRecorder {

    private final io.opentelemetry.metrics.DoubleValueRecorder.BoundDoubleValueRecorder
        agentBoundDoubleMeasure;

    public BoundInstrument(
        final io.opentelemetry.metrics.DoubleValueRecorder.BoundDoubleValueRecorder
            agentBoundDoubleMeasure) {
      this.agentBoundDoubleMeasure = agentBoundDoubleMeasure;
    }

    @Override
    public void record(final double delta) {
      agentBoundDoubleMeasure.record(delta);
    }

    @Override
    public void unbind() {
      agentBoundDoubleMeasure.unbind();
    }
  }

  static class Builder implements DoubleValueRecorder.Builder {

    private final io.opentelemetry.metrics.DoubleValueRecorder.Builder agentBuilder;

    public Builder(final io.opentelemetry.metrics.DoubleValueRecorder.Builder agentBuilder) {
      this.agentBuilder = agentBuilder;
    }

    @Override
    public DoubleValueRecorder.Builder setDescription(final String description) {
      agentBuilder.setDescription(description);
      return this;
    }

    @Override
    public DoubleValueRecorder.Builder setUnit(final String unit) {
      agentBuilder.setUnit(unit);
      return this;
    }

    @Override
    public DoubleValueRecorder.Builder setConstantLabels(final Labels constantLabels) {
      agentBuilder.setConstantLabels(LabelBridging.toAgent(constantLabels));
      return this;
    }

    @Override
    public DoubleValueRecorder build() {
      return new ApplicationDoubleValueRecorder(agentBuilder.build());
    }
  }
}
