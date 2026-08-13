/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import org.junit.jupiter.api.Test;

class ExperimentalConfigTest {

  @Test
  void messagingReceiveSpansDisabledByDefault() {
    assertThat(experimentalConfig(null, null).messagingReceiveSpansEnabled()).isFalse();
  }

  @Test
  void messagingReceiveSpansEnabled() {
    assertThat(experimentalConfig(true, null).messagingReceiveSpansEnabled()).isTrue();
  }

  @Test
  void deprecatedMessagingReceiveTelemetryIsUsedAsFallback() {
    assertThat(experimentalConfig(null, true).messagingReceiveSpansEnabled()).isTrue();
  }

  @Test
  void messagingReceiveSpansTakesPrecedenceOverDeprecatedReceiveTelemetry() {
    assertThat(experimentalConfig(false, true).messagingReceiveSpansEnabled()).isFalse();
  }

  private static ExperimentalConfig experimentalConfig(
      Boolean receiveSpansEnabled, Boolean receiveTelemetryEnabled) {
    ExtendedOpenTelemetry openTelemetry = mock(ExtendedOpenTelemetry.class);
    DeclarativeConfigProperties commonConfig =
        mock(DeclarativeConfigProperties.class, RETURNS_DEEP_STUBS);
    when(openTelemetry.getInstrumentationConfig("common")).thenReturn(commonConfig);
    when(commonConfig.get("messaging").get("receive_spans/development").getBoolean("enabled"))
        .thenReturn(receiveSpansEnabled);
    when(commonConfig
            .get("messaging")
            .get("receive_telemetry/development")
            .getBoolean("enabled", false))
        .thenReturn(Boolean.TRUE.equals(receiveTelemetryEnabled));
    return new ExperimentalConfig(openTelemetry);
  }
}
