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
import application.io.opentelemetry.metrics.LongSumObserver;
import io.opentelemetry.instrumentation.auto.opentelemetryapi.LabelBridging;
import io.opentelemetry.metrics.AsynchronousInstrument;

class ApplicationLongSumObserver implements LongSumObserver {

  private final io.opentelemetry.metrics.LongSumObserver agentLongSumObserver;

  protected ApplicationLongSumObserver(
      final io.opentelemetry.metrics.LongSumObserver agentLongSumObserver) {
    this.agentLongSumObserver = agentLongSumObserver;
  }

  @Override
  public void setCallback(final Callback<LongResult> metricUpdater) {
    agentLongSumObserver.setCallback(new AgentResultLongSumObserver(metricUpdater));
  }

  static class AgentResultLongSumObserver
      implements AsynchronousInstrument.Callback<
          io.opentelemetry.metrics.LongSumObserver.LongResult> {

    private final Callback<LongResult> metricUpdater;

    protected AgentResultLongSumObserver(final Callback<LongResult> metricUpdater) {
      this.metricUpdater = metricUpdater;
    }

    @Override
    public void update(final io.opentelemetry.metrics.LongSumObserver.LongResult result) {
      metricUpdater.update(new ApplicationResultLongSumObserver(result));
    }
  }

  static class ApplicationResultLongSumObserver implements LongResult {

    private final io.opentelemetry.metrics.LongSumObserver.LongResult agentResultLongSumObserver;

    public ApplicationResultLongSumObserver(
        final io.opentelemetry.metrics.LongSumObserver.LongResult agentResultLongSumObserver) {
      this.agentResultLongSumObserver = agentResultLongSumObserver;
    }

    @Override
    public void observe(final long value, final Labels labels) {
      agentResultLongSumObserver.observe(value, LabelBridging.toAgent(labels));
    }
  }

  static class Builder implements LongSumObserver.Builder {

    private final io.opentelemetry.metrics.LongSumObserver.Builder agentBuilder;

    protected Builder(final io.opentelemetry.metrics.LongSumObserver.Builder agentBuilder) {
      this.agentBuilder = agentBuilder;
    }

    @Override
    public LongSumObserver.Builder setDescription(final String description) {
      agentBuilder.setDescription(description);
      return this;
    }

    @Override
    public LongSumObserver.Builder setUnit(final String unit) {
      agentBuilder.setUnit(unit);
      return this;
    }

    @Override
    public LongSumObserver.Builder setConstantLabels(final Labels constantLabels) {
      agentBuilder.setConstantLabels(LabelBridging.toAgent(constantLabels));
      return this;
    }

    @Override
    public LongSumObserver build() {
      return new ApplicationLongSumObserver(agentBuilder.build());
    }
  }
}
