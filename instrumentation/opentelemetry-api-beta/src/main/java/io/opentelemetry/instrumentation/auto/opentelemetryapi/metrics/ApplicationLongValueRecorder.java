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
import application.io.opentelemetry.metrics.LongValueRecorder;
import io.opentelemetry.auto.instrumentation.opentelemetryapi.LabelBridging;

class ApplicationLongValueRecorder implements LongValueRecorder {

  private final io.opentelemetry.metrics.LongValueRecorder agentLongValueRecorder;

  protected ApplicationLongValueRecorder(
      final io.opentelemetry.metrics.LongValueRecorder agentLongValueRecorder) {
    this.agentLongValueRecorder = agentLongValueRecorder;
  }

  public io.opentelemetry.metrics.LongValueRecorder getAgentLongValueRecorder() {
    return agentLongValueRecorder;
  }

  @Override
  public void record(final long delta, final Labels labels) {
    agentLongValueRecorder.record(delta, LabelBridging.toAgent(labels));
  }

  @Override
  public BoundLongValueRecorder bind(final Labels labels) {
    return new BoundInstrument(agentLongValueRecorder.bind(LabelBridging.toAgent(labels)));
  }

  static class BoundInstrument implements LongValueRecorder.BoundLongValueRecorder {

    private final io.opentelemetry.metrics.LongValueRecorder.BoundLongValueRecorder
        agentBoundLongValueRecorder;

    protected BoundInstrument(
        final io.opentelemetry.metrics.LongValueRecorder.BoundLongValueRecorder
            agentBoundLongValueRecorder) {
      this.agentBoundLongValueRecorder = agentBoundLongValueRecorder;
    }

    @Override
    public void record(final long delta) {
      agentBoundLongValueRecorder.record(delta);
    }

    @Override
    public void unbind() {
      agentBoundLongValueRecorder.unbind();
    }
  }

  static class Builder implements LongValueRecorder.Builder {

    private final io.opentelemetry.metrics.LongValueRecorder.Builder agentBuilder;

    protected Builder(final io.opentelemetry.metrics.LongValueRecorder.Builder agentBuilder) {
      this.agentBuilder = agentBuilder;
    }

    @Override
    public LongValueRecorder.Builder setDescription(final String description) {
      agentBuilder.setDescription(description);
      return this;
    }

    @Override
    public LongValueRecorder.Builder setUnit(final String unit) {
      agentBuilder.setUnit(unit);
      return this;
    }

    @Override
    public LongValueRecorder.Builder setConstantLabels(final Labels constantLabels) {
      agentBuilder.setConstantLabels(LabelBridging.toAgent(constantLabels));
      return this;
    }

    @Override
    public LongValueRecorder build() {
      return new ApplicationLongValueRecorder(agentBuilder.build());
    }
  }
}
