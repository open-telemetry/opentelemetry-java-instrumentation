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
import application.io.opentelemetry.metrics.DoubleSumObserver;
import io.opentelemetry.instrumentation.auto.opentelemetryapi.LabelBridging;
import io.opentelemetry.metrics.AsynchronousInstrument;

class ApplicationDoubleSumObserver implements DoubleSumObserver {

  private final io.opentelemetry.metrics.DoubleSumObserver agentDoubleSumObserver;

  protected ApplicationDoubleSumObserver(
      io.opentelemetry.metrics.DoubleSumObserver agentDoubleSumObserver) {
    this.agentDoubleSumObserver = agentDoubleSumObserver;
  }

  @Override
  public void setCallback(Callback<DoubleResult> metricUpdater) {
    agentDoubleSumObserver.setCallback(new AgentResultDoubleSumObserver(metricUpdater));
  }

  static class AgentResultDoubleSumObserver
      implements AsynchronousInstrument.Callback<
          io.opentelemetry.metrics.DoubleSumObserver.DoubleResult> {

    private final Callback<DoubleResult> metricUpdater;

    protected AgentResultDoubleSumObserver(Callback<DoubleResult> metricUpdater) {
      this.metricUpdater = metricUpdater;
    }

    @Override
    public void update(io.opentelemetry.metrics.DoubleSumObserver.DoubleResult result) {
      metricUpdater.update(new ApplicationResultDoubleSumObserver(result));
    }
  }

  static class ApplicationResultDoubleSumObserver implements DoubleResult {

    private final io.opentelemetry.metrics.DoubleSumObserver.DoubleResult
        agentResultDoubleSumObserver;

    public ApplicationResultDoubleSumObserver(
        io.opentelemetry.metrics.DoubleSumObserver.DoubleResult agentResultDoubleSumObserver) {
      this.agentResultDoubleSumObserver = agentResultDoubleSumObserver;
    }

    @Override
    public void observe(double value, Labels labels) {
      agentResultDoubleSumObserver.observe(value, LabelBridging.toAgent(labels));
    }
  }

  static class Builder implements DoubleSumObserver.Builder {

    private final io.opentelemetry.metrics.DoubleSumObserver.Builder agentBuilder;

    protected Builder(io.opentelemetry.metrics.DoubleSumObserver.Builder agentBuilder) {
      this.agentBuilder = agentBuilder;
    }

    @Override
    public DoubleSumObserver.Builder setDescription(String description) {
      agentBuilder.setDescription(description);
      return this;
    }

    @Override
    public DoubleSumObserver.Builder setUnit(String unit) {
      agentBuilder.setUnit(unit);
      return this;
    }

    @Override
    public DoubleSumObserver build() {
      return new ApplicationDoubleSumObserver(agentBuilder.build());
    }
  }
}
