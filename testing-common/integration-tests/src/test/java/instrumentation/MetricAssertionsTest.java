/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package instrumentation;

import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class MetricAssertionsTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  void retriesAssertionsAcrossRepeatedObservableGaugeExports() {
    AtomicBoolean emitExpectedValue = new AtomicBoolean();
    testing
        .getOpenTelemetry()
        .getMeter("test-exhaustive-metric")
        .gaugeBuilder("retry.gauge")
        .ofLongs()
        .buildWithCallback(measurement -> measurement.record(emitExpectedValue.get() ? 1 : 0));

    testing.waitAndAssertMetricsExactly(
        "test-exhaustive-metric",
        metric -> {
          metric.hasName("retry.gauge");
          emitExpectedValue.set(true);
          metric.hasLongGaugeSatisfying(
              gauge -> gauge.hasPointsSatisfying(point -> point.hasValue(1)));
        });
  }
}
