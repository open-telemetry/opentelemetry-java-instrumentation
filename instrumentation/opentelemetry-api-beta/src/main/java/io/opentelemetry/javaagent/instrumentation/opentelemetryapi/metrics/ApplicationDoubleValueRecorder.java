/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.metrics;

import application.io.opentelemetry.common.Labels;
import application.io.opentelemetry.metrics.DoubleValueRecorder;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.LabelBridging;

class ApplicationDoubleValueRecorder implements DoubleValueRecorder {

  private final io.opentelemetry.metrics.DoubleValueRecorder agentDoubleValueRecorder;

  protected ApplicationDoubleValueRecorder(
      io.opentelemetry.metrics.DoubleValueRecorder agentDoubleValueRecorder) {
    this.agentDoubleValueRecorder = agentDoubleValueRecorder;
  }

  protected io.opentelemetry.metrics.DoubleValueRecorder getAgentDoubleValueRecorder() {
    return agentDoubleValueRecorder;
  }

  @Override
  public void record(double delta, Labels labels) {
    agentDoubleValueRecorder.record(delta, LabelBridging.toAgent(labels));
  }

  @Override
  public void record(double v) {
    agentDoubleValueRecorder.record(v);
  }

  @Override
  public BoundDoubleValueRecorder bind(Labels labels) {
    return new BoundInstrument(agentDoubleValueRecorder.bind(LabelBridging.toAgent(labels)));
  }

  static class BoundInstrument implements DoubleValueRecorder.BoundDoubleValueRecorder {

    private final io.opentelemetry.metrics.DoubleValueRecorder.BoundDoubleValueRecorder
        agentBoundDoubleMeasure;

    public BoundInstrument(
        io.opentelemetry.metrics.DoubleValueRecorder.BoundDoubleValueRecorder
            agentBoundDoubleMeasure) {
      this.agentBoundDoubleMeasure = agentBoundDoubleMeasure;
    }

    @Override
    public void record(double delta) {
      agentBoundDoubleMeasure.record(delta);
    }

    @Override
    public void unbind() {
      agentBoundDoubleMeasure.unbind();
    }
  }

  static class Builder implements DoubleValueRecorder.Builder {

    private final io.opentelemetry.metrics.DoubleValueRecorder.Builder agentBuilder;

    public Builder(io.opentelemetry.metrics.DoubleValueRecorder.Builder agentBuilder) {
      this.agentBuilder = agentBuilder;
    }

    @Override
    public DoubleValueRecorder.Builder setDescription(String description) {
      agentBuilder.setDescription(description);
      return this;
    }

    @Override
    public DoubleValueRecorder.Builder setUnit(String unit) {
      agentBuilder.setUnit(unit);
      return this;
    }

    @Override
    public DoubleValueRecorder build() {
      return new ApplicationDoubleValueRecorder(agentBuilder.build());
    }
  }
}
