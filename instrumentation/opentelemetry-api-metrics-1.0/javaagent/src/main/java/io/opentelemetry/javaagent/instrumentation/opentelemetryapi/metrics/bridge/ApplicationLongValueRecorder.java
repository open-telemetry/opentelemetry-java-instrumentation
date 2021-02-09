/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.metrics.bridge;

import application.io.opentelemetry.api.metrics.BoundLongValueRecorder;
import application.io.opentelemetry.api.metrics.LongValueRecorder;
import application.io.opentelemetry.api.metrics.LongValueRecorderBuilder;
import application.io.opentelemetry.api.metrics.common.Labels;

class ApplicationLongValueRecorder implements LongValueRecorder {

  private final io.opentelemetry.api.metrics.LongValueRecorder agentLongValueRecorder;

  protected ApplicationLongValueRecorder(
      io.opentelemetry.api.metrics.LongValueRecorder agentLongValueRecorder) {
    this.agentLongValueRecorder = agentLongValueRecorder;
  }

  public io.opentelemetry.api.metrics.LongValueRecorder getAgentLongValueRecorder() {
    return agentLongValueRecorder;
  }

  @Override
  public void record(long delta, Labels labels) {
    agentLongValueRecorder.record(delta, LabelBridging.toAgent(labels));
  }

  @Override
  public void record(long l) {
    agentLongValueRecorder.record(l);
  }

  @Override
  public BoundLongValueRecorder bind(Labels labels) {
    return new BoundInstrument(agentLongValueRecorder.bind(LabelBridging.toAgent(labels)));
  }

  static class BoundInstrument implements BoundLongValueRecorder {

    private final io.opentelemetry.api.metrics.BoundLongValueRecorder agentBoundLongValueRecorder;

    protected BoundInstrument(
        io.opentelemetry.api.metrics.BoundLongValueRecorder agentBoundLongValueRecorder) {
      this.agentBoundLongValueRecorder = agentBoundLongValueRecorder;
    }

    @Override
    public void record(long delta) {
      agentBoundLongValueRecorder.record(delta);
    }

    @Override
    public void unbind() {
      agentBoundLongValueRecorder.unbind();
    }
  }

  static class Builder implements LongValueRecorderBuilder {

    private final io.opentelemetry.api.metrics.LongValueRecorderBuilder agentBuilder;

    protected Builder(io.opentelemetry.api.metrics.LongValueRecorderBuilder agentBuilder) {
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
    public LongValueRecorder build() {
      return new ApplicationLongValueRecorder(agentBuilder.build());
    }
  }
}
