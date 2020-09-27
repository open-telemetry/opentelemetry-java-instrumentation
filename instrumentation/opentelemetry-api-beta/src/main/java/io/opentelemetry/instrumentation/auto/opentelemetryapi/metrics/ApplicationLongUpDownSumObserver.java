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
import application.io.opentelemetry.metrics.LongUpDownSumObserver;
import io.opentelemetry.instrumentation.auto.opentelemetryapi.LabelBridging;
import io.opentelemetry.metrics.AsynchronousInstrument;

class ApplicationLongUpDownSumObserver implements LongUpDownSumObserver {

  private final io.opentelemetry.metrics.LongUpDownSumObserver agentLongUpDownSumObserver;

  protected ApplicationLongUpDownSumObserver(
      io.opentelemetry.metrics.LongUpDownSumObserver agentLongUpDownSumObserver) {
    this.agentLongUpDownSumObserver = agentLongUpDownSumObserver;
  }

  @Override
  public void setCallback(Callback<LongResult> metricUpdater) {
    agentLongUpDownSumObserver.setCallback(new AgentResultLongUpDownSumObserver(metricUpdater));
  }

  static class AgentResultLongUpDownSumObserver
      implements AsynchronousInstrument.Callback<
          io.opentelemetry.metrics.LongUpDownSumObserver.LongResult> {

    private final Callback<LongResult> metricUpdater;

    protected AgentResultLongUpDownSumObserver(Callback<LongResult> metricUpdater) {
      this.metricUpdater = metricUpdater;
    }

    @Override
    public void update(io.opentelemetry.metrics.LongUpDownSumObserver.LongResult result) {
      metricUpdater.update(new ApplicationResultLongUpDownSumObserver(result));
    }
  }

  static class ApplicationResultLongUpDownSumObserver implements LongResult {

    private final io.opentelemetry.metrics.LongUpDownSumObserver.LongResult
        agentResultLongUpDownSumObserver;

    public ApplicationResultLongUpDownSumObserver(
        io.opentelemetry.metrics.LongUpDownSumObserver.LongResult
            agentResultLongUpDownSumObserver) {
      this.agentResultLongUpDownSumObserver = agentResultLongUpDownSumObserver;
    }

    @Override
    public void observe(long value, Labels labels) {
      agentResultLongUpDownSumObserver.observe(value, LabelBridging.toAgent(labels));
    }
  }

  static class Builder implements LongUpDownSumObserver.Builder {

    private final io.opentelemetry.metrics.LongUpDownSumObserver.Builder agentBuilder;

    protected Builder(io.opentelemetry.metrics.LongUpDownSumObserver.Builder agentBuilder) {
      this.agentBuilder = agentBuilder;
    }

    @Override
    public LongUpDownSumObserver.Builder setDescription(String description) {
      agentBuilder.setDescription(description);
      return this;
    }

    @Override
    public LongUpDownSumObserver.Builder setUnit(String unit) {
      agentBuilder.setUnit(unit);
      return this;
    }

    @Override
    public LongUpDownSumObserver build() {
      return new ApplicationLongUpDownSumObserver(agentBuilder.build());
    }
  }
}
