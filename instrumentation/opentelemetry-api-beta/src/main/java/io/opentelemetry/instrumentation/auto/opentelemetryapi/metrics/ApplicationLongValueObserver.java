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
import application.io.opentelemetry.metrics.LongValueObserver;
import io.opentelemetry.instrumentation.auto.opentelemetryapi.LabelBridging;

class ApplicationLongValueObserver implements LongValueObserver {

  private final io.opentelemetry.metrics.LongValueObserver agentLongValueObserver;

  public ApplicationLongValueObserver(
      io.opentelemetry.metrics.LongValueObserver agentLongValueObserver) {
    this.agentLongValueObserver = agentLongValueObserver;
  }

  @Override
  public void setCallback(Callback<LongResult> metricUpdater) {
    agentLongValueObserver.setCallback(new AgentResultLongValueObserver(metricUpdater));
  }

  public static class AgentResultLongValueObserver
      implements io.opentelemetry.metrics.AsynchronousInstrument.Callback<
          io.opentelemetry.metrics.LongValueObserver.LongResult> {

    private final Callback<LongResult> metricUpdater;

    public AgentResultLongValueObserver(Callback<LongResult> metricUpdater) {
      this.metricUpdater = metricUpdater;
    }

    @Override
    public void update(io.opentelemetry.metrics.LongValueObserver.LongResult result) {
      metricUpdater.update(new ApplicationResultLongValueObserver(result));
    }
  }

  public static class ApplicationResultLongValueObserver implements LongResult {

    private final io.opentelemetry.metrics.LongValueObserver.LongResult
        agentResultLongValueObserver;

    public ApplicationResultLongValueObserver(
        io.opentelemetry.metrics.LongValueObserver.LongResult agentResultLongValueObserver) {
      this.agentResultLongValueObserver = agentResultLongValueObserver;
    }

    @Override
    public void observe(long value, Labels labels) {
      agentResultLongValueObserver.observe(value, LabelBridging.toAgent(labels));
    }
  }

  static class Builder implements LongValueObserver.Builder {

    private final io.opentelemetry.metrics.LongValueObserver.Builder agentBuilder;

    public Builder(io.opentelemetry.metrics.LongValueObserver.Builder agentBuilder) {
      this.agentBuilder = agentBuilder;
    }

    @Override
    public LongValueObserver.Builder setDescription(String description) {
      agentBuilder.setDescription(description);
      return this;
    }

    @Override
    public LongValueObserver.Builder setUnit(String unit) {
      agentBuilder.setUnit(unit);
      return this;
    }

    @Override
    public LongValueObserver build() {
      return new ApplicationLongValueObserver(agentBuilder.build());
    }
  }
}
