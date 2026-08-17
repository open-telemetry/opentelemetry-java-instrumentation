/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import org.junit.jupiter.api.Test;

class MessagingConfigTest {

  @Test
  void readsSelectorFromCommonMessagingConfig() {
    ExtendedOpenTelemetry openTelemetry = mockOpenTelemetry();
    when(messagingConfig(openTelemetry)
            .get("headers/development")
            .getScalarList("included", String.class))
        .thenReturn(singletonList("Test-*"));

    assertThat(MessagingConfig.getHeaders(openTelemetry).getIncluded()).containsExactly("Test-*");
  }

  @Test
  void readsDeprecatedSelectorFromCommonMessagingConfig() {
    ExtendedOpenTelemetry openTelemetry = mockOpenTelemetry();
    when(messagingConfig(openTelemetry).getScalarList("capture_headers/development", String.class))
        .thenReturn(singletonList("deprecated"));

    assertThat(MessagingConfig.getHeaders(openTelemetry).getIncluded())
        .containsExactly("deprecated");
  }

  @Test
  void absentSelectorCapturesNothing() {
    assertThat(MessagingConfig.getHeaders(mockOpenTelemetry()).isEmpty()).isTrue();
  }

  @Test
  void systemPropertyFallbackIsOnlyUsedWhenEnabled() {
    ExtendedOpenTelemetry openTelemetry = mockOpenTelemetry();
    System.setProperty("otel.instrumentation.messaging.experimental.headers.included", "from-prop");
    try {
      assertThat(MessagingConfig.getHeaders(openTelemetry, false).isEmpty()).isTrue();
      assertThat(MessagingConfig.getHeaders(openTelemetry, true).getIncluded())
          .containsExactly("from-prop");
    } finally {
      System.clearProperty("otel.instrumentation.messaging.experimental.headers.included");
    }
  }

  private static ExtendedOpenTelemetry mockOpenTelemetry() {
    ExtendedOpenTelemetry openTelemetry = mock(ExtendedOpenTelemetry.class);
    DeclarativeConfigProperties commonConfig =
        mock(DeclarativeConfigProperties.class, RETURNS_DEEP_STUBS);
    when(openTelemetry.getInstrumentationConfig("common")).thenReturn(commonConfig);
    DeclarativeConfigProperties messagingConfig = commonConfig.get("messaging");
    when(messagingConfig.get("headers/development").getScalarList("included", String.class))
        .thenReturn(null);
    when(messagingConfig.get("headers/development").getScalarList("excluded", String.class))
        .thenReturn(null);
    when(messagingConfig.getScalarList("capture_headers/development", String.class))
        .thenReturn(null);
    return openTelemetry;
  }

  private static DeclarativeConfigProperties messagingConfig(ExtendedOpenTelemetry openTelemetry) {
    return openTelemetry.getInstrumentationConfig("common").get("messaging");
  }
}
