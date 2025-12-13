/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.javaagent.tooling.OpenTelemetryInstaller;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.SetSystemProperty;

class OtlpProtocolPropertiesSupplierTest {

  @BeforeEach
  @AfterEach
  void setUp() {
    GlobalOpenTelemetry.resetForTest();
  }

  @SetSystemProperty(
      key = "otel.exporter.otlp.protocol",
      value = "grpc") // user explicitly sets grpc
  @Test
  void keepUserOtlpProtocolConfiguration() {
    // when
    AutoConfiguredOpenTelemetrySdk autoConfiguredSdk =
        OpenTelemetryInstaller.installOpenTelemetrySdk(
            this.getClass().getClassLoader(), EarlyInitAgentConfig.create());

    // then
    assertThat(autoConfiguredSdk.getOpenTelemetrySdk().toString())
        .contains("OtlpGrpc")
        .doesNotContain("OtlpHttp");
  }

  @Test
  void defaultHttpProtobufOtlpProtocolConfiguration() {
    // when
    AutoConfiguredOpenTelemetrySdk autoConfiguredSdk =
        OpenTelemetryInstaller.installOpenTelemetrySdk(
            this.getClass().getClassLoader(), EarlyInitAgentConfig.create());

    // then
    assertThat(autoConfiguredSdk.getOpenTelemetrySdk().toString())
        .contains("OtlpHttp")
        .doesNotContain("OtlpGrpc");
  }
}
