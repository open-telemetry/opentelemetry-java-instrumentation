/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.metrics;

import application.io.opentelemetry.api.common.Labels;
import application.io.opentelemetry.api.metrics.LongValueRecorder;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.LabelBridging;

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

  static class BoundInstrument implements LongValueRecorder.BoundLongValueRecorder {

    private final io.opentelemetry.api.metrics.LongValueRecorder.BoundLongValueRecorder
        agentBoundLongValueRecorder;

    protected BoundInstrument(
        io.opentelemetry.api.metrics.LongValueRecorder.BoundLongValueRecorder
            agentBoundLongValueRecorder) {
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

  static class Builder implements LongValueRecorder.Builder {

    private final io.opentelemetry.api.metrics.LongValueRecorder.Builder agentBuilder;

    protected Builder(io.opentelemetry.api.metrics.LongValueRecorder.Builder agentBuilder) {
      this.agentBuilder = agentBuilder;
    }

    @Override
    public LongValueRecorder.Builder setDescription(String description) {
      agentBuilder.setDescription(description);
      return this;
    }

    @Override
    public LongValueRecorder.Builder setUnit(String unit) {
      agentBuilder.setUnit(unit);
      return this;
    }

    @Override
    public LongValueRecorder build() {
      return new ApplicationLongValueRecorder(agentBuilder.build());
    }
  }
}
