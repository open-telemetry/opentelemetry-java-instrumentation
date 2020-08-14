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
import application.io.opentelemetry.metrics.DoubleUpDownSumObserver;
import io.opentelemetry.instrumentation.auto.opentelemetryapi.LabelBridging;
import io.opentelemetry.metrics.AsynchronousInstrument;

class ApplicationDoubleUpDownSumObserver implements DoubleUpDownSumObserver {

  private final io.opentelemetry.metrics.DoubleUpDownSumObserver agentDoubleUpDownSumObserver;

  protected ApplicationDoubleUpDownSumObserver(
      final io.opentelemetry.metrics.DoubleUpDownSumObserver agentDoubleUpDownSumObserver) {
    this.agentDoubleUpDownSumObserver = agentDoubleUpDownSumObserver;
  }

  @Override
  public void setCallback(final Callback<DoubleResult> metricUpdater) {
    agentDoubleUpDownSumObserver.setCallback(new AgentResultDoubleUpDownSumObserver(metricUpdater));
  }

  static class AgentResultDoubleUpDownSumObserver
      implements AsynchronousInstrument.Callback<
          io.opentelemetry.metrics.DoubleUpDownSumObserver.DoubleResult> {

    private final Callback<DoubleResult> metricUpdater;

    protected AgentResultDoubleUpDownSumObserver(final Callback<DoubleResult> metricUpdater) {
      this.metricUpdater = metricUpdater;
    }

    @Override
    public void update(final io.opentelemetry.metrics.DoubleUpDownSumObserver.DoubleResult result) {
      metricUpdater.update(new ApplicationResultDoubleUpDownSumObserver(result));
    }
  }

  static class ApplicationResultDoubleUpDownSumObserver implements DoubleResult {

    private final io.opentelemetry.metrics.DoubleUpDownSumObserver.DoubleResult
        agentResultDoubleUpDownSumObserver;

    public ApplicationResultDoubleUpDownSumObserver(
        final io.opentelemetry.metrics.DoubleUpDownSumObserver.DoubleResult
            agentResultDoubleUpDownSumObserver) {
      this.agentResultDoubleUpDownSumObserver = agentResultDoubleUpDownSumObserver;
    }

    @Override
    public void observe(final double value, final Labels labels) {
      agentResultDoubleUpDownSumObserver.observe(value, LabelBridging.toAgent(labels));
    }
  }

  static class Builder implements DoubleUpDownSumObserver.Builder {

    private final io.opentelemetry.metrics.DoubleUpDownSumObserver.Builder agentBuilder;

    protected Builder(final io.opentelemetry.metrics.DoubleUpDownSumObserver.Builder agentBuilder) {
      this.agentBuilder = agentBuilder;
    }

    @Override
    public DoubleUpDownSumObserver.Builder setDescription(final String description) {
      agentBuilder.setDescription(description);
      return this;
    }

    @Override
    public DoubleUpDownSumObserver.Builder setUnit(final String unit) {
      agentBuilder.setUnit(unit);
      return this;
    }

    @Override
    public DoubleUpDownSumObserver.Builder setConstantLabels(final Labels constantLabels) {
      agentBuilder.setConstantLabels(LabelBridging.toAgent(constantLabels));
      return this;
    }

    @Override
    public DoubleUpDownSumObserver build() {
      return new ApplicationDoubleUpDownSumObserver(agentBuilder.build());
    }
  }
}
