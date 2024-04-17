/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.kafka.v2_7;

import static java.util.Collections.emptyList;

import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.testing.AbstractSpringKafkaNoReceiveTelemetryTest;
import java.util.List;
import org.junit.jupiter.api.extension.RegisterExtension;

class SpringKafkaNoReceiveTelemetryTest extends AbstractSpringKafkaNoReceiveTelemetryTest {

  @RegisterExtension
  protected static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }

  @Override
  protected List<Class<?>> additionalSpringConfigs() {
    return emptyList();
  }

  @Override
  protected boolean isLibraryInstrumentationTest() {
    return false;
  }
}
