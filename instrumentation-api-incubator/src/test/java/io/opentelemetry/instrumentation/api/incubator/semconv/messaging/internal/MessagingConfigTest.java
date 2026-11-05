/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
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
  void readsSelectorFromCommonMessagingConfig() {
    ExtendedOpenTelemetry openTelemetry = mockOpenTelemetry();
    when(messagingConfig(openTelemetry).get("headers").getScalarList("included", String.class))
        .thenReturn(singletonList("Test-*"));

    assertThat(MessagingConfig.getHeaders(openTelemetry).getIncluded()).containsExactly("Test-*");
  }

  @Test
  void stableSelectorUsesCaseSensitiveGlobsAndExclusionPrecedence() {
    ExtendedOpenTelemetry openTelemetry = mockOpenTelemetry();
    when(messagingConfig(openTelemetry).get("headers").getScalarList("included", String.class))
        .thenReturn(singletonList("Test-?*"));
    when(messagingConfig(openTelemetry).get("headers").getScalarList("excluded", String.class))
        .thenReturn(singletonList("Test-secret"));

    IncludeExclude headers = MessagingConfig.getHeaders(openTelemetry);

    assertThat(headers.matches("Test-public")).isTrue();
    assertThat(headers.matches("test-public")).isFalse();
    assertThat(headers.matches("Test-secret")).isFalse();
  }

  @Test
  void stableExcludeOnlySelectorCapturesAllOtherHeaders() {
    ExtendedOpenTelemetry openTelemetry = mockOpenTelemetry();
    when(messagingConfig(openTelemetry).get("headers").getScalarList("excluded", String.class))
        .thenReturn(singletonList("Secret-*"));

    IncludeExclude headers = MessagingConfig.getHeaders(openTelemetry);

    assertThat(headers.matches("public")).isTrue();
    assertThat(headers.matches("Secret-token")).isFalse();
  }

  @Test
  void emptyStableSelectorCapturesNothing() {
    ExtendedOpenTelemetry openTelemetry = mockOpenTelemetry();
    when(messagingConfig(openTelemetry).get("headers").getScalarList("included", String.class))
        .thenReturn(emptyList());

    assertThat(MessagingConfig.getHeaders(openTelemetry).isEmpty()).isTrue();
  }

  @Test
  void systemPropertyFallbackIsOnlyUsedWhenEnabled() {
    ExtendedOpenTelemetry openTelemetry = mockOpenTelemetry();
    String property = "otel.instrumentation.common.messaging.headers.included";
    System.setProperty(property, "from-prop");
    try {
      assertThat(MessagingConfig.getHeaders(openTelemetry, false).isEmpty()).isTrue();
      assertThat(MessagingConfig.getHeaders(openTelemetry, true).getIncluded())
          .containsExactly("from-prop");
    } finally {
      System.clearProperty(property);
    }
  }

  @Test
  void flatSelectorLeavesResolveIndependently() {
    ExtendedOpenTelemetry openTelemetry = mockOpenTelemetry();
    String stableIncluded = "otel.instrumentation.common.messaging.headers.included";
    String stableExcluded = "otel.instrumentation.common.messaging.headers.excluded";
    System.setProperty(stableIncluded, "*");
    System.setProperty(stableExcluded, "excluded");
    try {
      IncludeExclude headers = MessagingConfig.getHeaders(openTelemetry, true);
      assertThat(headers.getIncluded()).containsExactly("*");
      assertThat(headers.getExcluded()).containsExactly("excluded");
    } finally {
      System.clearProperty(stableIncluded);
      System.clearProperty(stableExcluded);
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
    when(messagingConfig.get("headers").getScalarList("included", String.class)).thenReturn(null);
    when(messagingConfig.get("headers").getScalarList("excluded", String.class)).thenReturn(null);
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
