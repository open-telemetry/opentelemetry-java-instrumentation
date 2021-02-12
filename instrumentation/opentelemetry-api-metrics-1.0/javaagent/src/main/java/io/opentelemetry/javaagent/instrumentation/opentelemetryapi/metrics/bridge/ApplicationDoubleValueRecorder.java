/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.metrics.bridge;

import application.io.opentelemetry.api.metrics.BoundDoubleValueRecorder;
import application.io.opentelemetry.api.metrics.DoubleValueRecorder;
import application.io.opentelemetry.api.metrics.DoubleValueRecorderBuilder;
import application.io.opentelemetry.api.metrics.common.Labels;

class ApplicationDoubleValueRecorder implements DoubleValueRecorder {

  private final io.opentelemetry.api.metrics.DoubleValueRecorder agentDoubleValueRecorder;

  protected ApplicationDoubleValueRecorder(
      io.opentelemetry.api.metrics.DoubleValueRecorder agentDoubleValueRecorder) {
    this.agentDoubleValueRecorder = agentDoubleValueRecorder;
  }

  protected io.opentelemetry.api.metrics.DoubleValueRecorder getAgentDoubleValueRecorder() {
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

  static class BoundInstrument implements BoundDoubleValueRecorder {

    private final io.opentelemetry.api.metrics.BoundDoubleValueRecorder agentBoundDoubleMeasure;

    public BoundInstrument(
        io.opentelemetry.api.metrics.BoundDoubleValueRecorder agentBoundDoubleMeasure) {
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

  static class Builder implements DoubleValueRecorderBuilder {

    private final io.opentelemetry.api.metrics.DoubleValueRecorderBuilder agentBuilder;

    public Builder(io.opentelemetry.api.metrics.DoubleValueRecorderBuilder agentBuilder) {
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
    public DoubleValueRecorder build() {
      return new ApplicationDoubleValueRecorder(agentBuilder.build());
    }
  }
}
