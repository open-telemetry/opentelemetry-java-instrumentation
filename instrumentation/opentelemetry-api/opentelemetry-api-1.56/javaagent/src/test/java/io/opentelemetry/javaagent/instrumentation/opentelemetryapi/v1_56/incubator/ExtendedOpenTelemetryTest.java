/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_56.incubator;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class ExtendedOpenTelemetryTest {
  @RegisterExtension
  static final AgentInstrumentationExtension testing = AgentInstrumentationExtension.create();

  private OpenTelemetry openTelemetry;

  @BeforeEach
  void setup() {
    openTelemetry = GlobalOpenTelemetry.get();
  }

  @Test
  void getConfig() {
    assertThat(openTelemetry).isInstanceOf(ExtendedOpenTelemetry.class);

    DeclarativeConfigProperties instrumentationConfig =
        ((ExtendedOpenTelemetry) openTelemetry).getConfigProvider().getInstrumentationConfig();
    assertThat(instrumentationConfig).isNotNull();

    assertThat(instrumentationConfig.getStructured("java").getStructured("foo").getString("bar"))
        .isEqualTo("baz");
  }
}
