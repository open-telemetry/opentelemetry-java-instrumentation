/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@ResourceLock(Resources.SYSTEM_PROPERTIES)
class MessagingConfigTest {

  @Test
  void readsExactNamesFromCommonMessagingConfig() {
    ExtendedOpenTelemetry openTelemetry = mockOpenTelemetry();
    when(messagingConfig(openTelemetry).getScalarList("captured_headers", String.class))
        .thenReturn(asList("Test-Header", "*", "Test-?"));

    assertThat(MessagingConfig.getCapturedHeaders(openTelemetry))
        .containsExactly("Test-Header", "*", "Test-?");
  }

  @Test
  void defaultsToNoCapturedHeaders() {
    assertThat(MessagingConfig.getCapturedHeaders(mockOpenTelemetry())).isEmpty();
  }

  @Test
  void emptyListCapturesNothing() {
    ExtendedOpenTelemetry openTelemetry = mockOpenTelemetry();
    when(messagingConfig(openTelemetry).getScalarList("captured_headers", String.class))
        .thenReturn(emptyList());

    assertThat(MessagingConfig.getCapturedHeaders(openTelemetry)).isEmpty();
  }

  @Test
  void systemPropertyFallbackIsOnlyUsedWhenEnabled() {
    ExtendedOpenTelemetry openTelemetry = mockOpenTelemetry();
    String property = "otel.instrumentation.common.messaging.captured-headers";
    System.setProperty(property, "Test-Header,*,Test-?");
    try {
      assertThat(MessagingConfig.getCapturedHeaders(openTelemetry, false)).isEmpty();
      assertThat(MessagingConfig.getCapturedHeaders(openTelemetry, true))
          .containsExactly("Test-Header", "*", "Test-?");
    } finally {
      System.clearProperty(property);
    }
  }

  @Test
  void systemPropertyFallbackWithoutDeclarativeConfigProvider() {
    String property = "otel.instrumentation.common.messaging.captured-headers";
    System.setProperty(property, "Test-Header,*,Test-?");
    try {
      assertThat(MessagingConfig.getCapturedHeaders(OpenTelemetry.noop())).isEmpty();
      assertThat(MessagingConfig.getCapturedHeaders(OpenTelemetry.noop(), true))
          .containsExactly("Test-Header", "*", "Test-?");
      System.setProperty(property, "");
      assertThat(MessagingConfig.getCapturedHeaders(OpenTelemetry.noop(), true)).isEmpty();
    } finally {
      System.clearProperty(property);
    }
  }

  @Test
  void declarativeConfigurationTakesPrecedenceOverSystemPropertyFallback() {
    ExtendedOpenTelemetry openTelemetry = mockOpenTelemetry();
    String property = "otel.instrumentation.common.messaging.captured-headers";
    System.setProperty(property, "Authorization");
    try {
      when(messagingConfig(openTelemetry).getScalarList("captured_headers", String.class))
          .thenReturn(singletonList("Test-Header"));
      assertThat(MessagingConfig.getCapturedHeaders(openTelemetry, true))
          .containsExactly("Test-Header");

      when(messagingConfig(openTelemetry).getScalarList("captured_headers", String.class))
          .thenReturn(emptyList());
      assertThat(MessagingConfig.getCapturedHeaders(openTelemetry, true)).isEmpty();
    } finally {
      System.clearProperty(property);
    }
  }

  @Test
  void resolvesReceiveTelemetryConfig() {
    ExtendedOpenTelemetry openTelemetry = mockOpenTelemetry();
    when(messagingConfig(openTelemetry).get("receive_telemetry/development").getBoolean("enabled"))
        .thenReturn(true);

    assertThat(MessagingConfig.isReceiveTelemetryEnabled(openTelemetry, false)).isTrue();
  }

  @Test
  void receiveTelemetryIsDisabledByDefault() {
    assertThat(MessagingConfig.isReceiveTelemetryEnabled(mockOpenTelemetry(), false)).isFalse();
  }

  @Test
  void readsReceiveTelemetrySystemProperty() {
    ExtendedOpenTelemetry openTelemetry = mockOpenTelemetry();
    String property =
        "otel.instrumentation.common.messaging.experimental.receive-telemetry.enabled";
    System.setProperty(property, "true");
    try {
      assertThat(MessagingConfig.isReceiveTelemetryEnabled(openTelemetry, true)).isTrue();
    } finally {
      System.clearProperty(property);
    }
  }

  @ParameterizedTest
  @MethodSource("messageCreateSpansCases")
  void resolvesMessageCreateSpans(
      Boolean instrumentationValue, Boolean commonValue, boolean expected) {
    ExtendedOpenTelemetry openTelemetry = mockOpenTelemetry();
    when(messageCreateSpansConfig(instrumentationConfig(openTelemetry)).getBoolean("enabled"))
        .thenReturn(instrumentationValue);
    when(messageCreateSpansConfig(messagingConfig(openTelemetry)).getBoolean("enabled"))
        .thenReturn(commonValue);

    assertThat(MessagingConfig.isBatchSendMessageCreationSpansEnabled(openTelemetry, "aws_sdk"))
        .isEqualTo(expected);
  }

  @Test
  void messageCreateSpansSystemPropertyFallbackIsOnlyUsedWhenEnabled() {
    ExtendedOpenTelemetry openTelemetry = mockOpenTelemetry();
    String property = "otel.instrumentation.common.messaging.message-create-spans.enabled";
    System.setProperty(property, "false");
    try {
      assertThat(
              MessagingConfig.isBatchSendMessageCreationSpansEnabled(
                  openTelemetry, "aws_sdk", false))
          .isTrue();
      assertThat(
              MessagingConfig.isBatchSendMessageCreationSpansEnabled(
                  openTelemetry, "aws_sdk", true))
          .isFalse();
    } finally {
      System.clearProperty(property);
    }
  }

  @Test
  void instrumentationSystemPropertyOverridesCommonDeclarativeConfig() {
    ExtendedOpenTelemetry openTelemetry = mockOpenTelemetry();
    when(messageCreateSpansConfig(messagingConfig(openTelemetry)).getBoolean("enabled"))
        .thenReturn(true);
    String property = "otel.instrumentation.aws-sdk.message-create-spans.enabled";
    System.setProperty(property, "false");
    try {
      assertThat(
              MessagingConfig.isBatchSendMessageCreationSpansEnabled(
                  openTelemetry, "aws_sdk", true))
          .isFalse();
    } finally {
      System.clearProperty(property);
    }
  }

  private static ExtendedOpenTelemetry mockOpenTelemetry() {
    ExtendedOpenTelemetry openTelemetry = mock(ExtendedOpenTelemetry.class);
    DeclarativeConfigProperties commonConfig =
        mock(DeclarativeConfigProperties.class, RETURNS_DEEP_STUBS);
    when(openTelemetry.getInstrumentationConfig("common")).thenReturn(commonConfig);
    DeclarativeConfigProperties instrumentationConfig =
        mock(DeclarativeConfigProperties.class, RETURNS_DEEP_STUBS);
    when(openTelemetry.getInstrumentationConfig("aws_sdk")).thenReturn(instrumentationConfig);
    DeclarativeConfigProperties messagingConfig = commonConfig.get("messaging");
    when(messagingConfig.get("receive_telemetry/development").getBoolean("enabled"))
        .thenReturn(null);
    when(messageCreateSpansConfig(instrumentationConfig).getBoolean("enabled")).thenReturn(null);
    when(messageCreateSpansConfig(messagingConfig).getBoolean("enabled")).thenReturn(null);
    when(messagingConfig.getScalarList("captured_headers", String.class)).thenReturn(null);
    return openTelemetry;
  }

  private static Stream<Arguments> messageCreateSpansCases() {
    return Stream.of(
        argumentSet("default", null, null, true),
        argumentSet("common fallback", null, false, false),
        argumentSet("instrumentation true overrides common false", true, false, true),
        argumentSet("instrumentation false overrides common true", false, true, false));
  }

  private static DeclarativeConfigProperties messageCreateSpansConfig(
      DeclarativeConfigProperties config) {
    return config.get("message_create_spans");
  }

  private static DeclarativeConfigProperties instrumentationConfig(
      ExtendedOpenTelemetry openTelemetry) {
    return openTelemetry.getInstrumentationConfig("aws_sdk");
  }

  private static DeclarativeConfigProperties messagingConfig(ExtendedOpenTelemetry openTelemetry) {
    return openTelemetry.getInstrumentationConfig("common").get("messaging");
  }
}
