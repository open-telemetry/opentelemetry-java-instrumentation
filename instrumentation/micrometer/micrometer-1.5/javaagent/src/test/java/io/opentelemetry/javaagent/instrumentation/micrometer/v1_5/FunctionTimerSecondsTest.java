/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.micrometer.v1_5;

import io.opentelemetry.instrumentation.micrometer.v1_5.AbstractFunctionTimerSecondsTest;
import io.opentelemetry.instrumentation.micrometer.v1_5.OpenTelemetryMeterRegistryBuilder;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.extension.RegisterExtension;

class FunctionTimerSecondsTest extends AbstractFunctionTimerSecondsTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension
  static final MicrometerTestingExtension micrometerExtension =
      new MicrometerTestingExtension(testing) {
        @Override
        OpenTelemetryMeterRegistryBuilder configureOtelRegistry(
            OpenTelemetryMeterRegistryBuilder registry) {
          return registry.setBaseTimeUnit(TimeUnit.SECONDS);
        }
      };

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }
}
