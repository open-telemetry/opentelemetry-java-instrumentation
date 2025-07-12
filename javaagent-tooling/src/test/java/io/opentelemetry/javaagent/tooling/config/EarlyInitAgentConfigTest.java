/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.javaagent.tooling.OpenTelemetryInstaller;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EarlyInitAgentConfigTest {

  @BeforeEach
  void setUp() {
    GlobalOpenTelemetry.resetForTest();
  }

  @Test
  void globalOpenTelemetry() {
    AutoConfiguredOpenTelemetrySdk sdk =
        OpenTelemetryInstaller.installOpenTelemetrySdk(
            EarlyInitAgentConfig.class.getClassLoader(), EarlyInitAgentConfig.create());

    assertThat(sdk).isNotNull().isNotEqualTo(OpenTelemetry.noop());
  }
}
