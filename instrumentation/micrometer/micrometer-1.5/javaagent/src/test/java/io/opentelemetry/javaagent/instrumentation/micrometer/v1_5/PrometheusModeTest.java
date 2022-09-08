/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.micrometer.v1_5;

import io.opentelemetry.instrumentation.micrometer.v1_5.AbstractPrometheusModeTest;
import io.opentelemetry.instrumentation.micrometer.v1_5.OpenTelemetryMeterRegistryBuilder;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.junit.jupiter.api.extension.RegisterExtension;

class PrometheusModeTest extends AbstractPrometheusModeTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension
  static final MicrometerTestingExtension micrometerExtension =
      new MicrometerTestingExtension(testing) {
        @Override
        OpenTelemetryMeterRegistryBuilder configureOtelRegistry(
            OpenTelemetryMeterRegistryBuilder registry) {
          return registry.setPrometheusMode(true);
        }
      };

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }
}
