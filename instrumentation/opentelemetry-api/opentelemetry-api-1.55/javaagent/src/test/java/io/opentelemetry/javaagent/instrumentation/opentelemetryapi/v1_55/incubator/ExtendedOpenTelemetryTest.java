/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_55.incubator;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class ExtendedOpenTelemetryTest {
  @RegisterExtension
  static final AgentInstrumentationExtension testing = AgentInstrumentationExtension.create();

  private ExtendedOpenTelemetry extendedOpenTelemetry;

  @BeforeEach
  void setup() {
    extendedOpenTelemetry = (ExtendedOpenTelemetry) GlobalOpenTelemetry.get();
  }

  @Test
  void getConfig() {
    DeclarativeConfigProperties instrumentationConfig =
        extendedOpenTelemetry.getConfigProvider().getInstrumentationConfig();
    assertThat(instrumentationConfig).isNull();
  }
}
