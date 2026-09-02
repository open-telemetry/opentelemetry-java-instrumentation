/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

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

  @ParameterizedTest
  @MethodSource("batchSendMessageCreationSpansCases")
  void resolvesBatchSendMessageCreationSpans(
      Boolean instrumentationValue, Boolean commonValue, boolean expected) {
    ExtendedOpenTelemetry openTelemetry = mockOpenTelemetry();
    when(batchSendMessageCreationSpansConfig(instrumentationConfig(openTelemetry))
            .getBoolean("enabled"))
        .thenReturn(instrumentationValue);
    when(batchSendMessageCreationSpansConfig(messagingConfig(openTelemetry)).getBoolean("enabled"))
        .thenReturn(commonValue);

    assertThat(MessagingConfig.isBatchSendMessageCreationSpansEnabled(openTelemetry, "aws_sdk"))
        .isEqualTo(expected);
  }

  @Test
  void batchSendMessageCreationSpansSystemPropertyFallbackIsOnlyUsedWhenEnabled() {
    ExtendedOpenTelemetry openTelemetry = mockOpenTelemetry();
    System.setProperty(
        "otel.instrumentation.messaging.batch-send.message-creation-spans.enabled", "false");
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
      System.clearProperty(
          "otel.instrumentation.messaging.batch-send.message-creation-spans.enabled");
    }
  }

  @Test
  void instrumentationSystemPropertyOverridesCommonDeclarativeConfig() {
    ExtendedOpenTelemetry openTelemetry = mockOpenTelemetry();
    when(batchSendMessageCreationSpansConfig(messagingConfig(openTelemetry)).getBoolean("enabled"))
        .thenReturn(true);
    System.setProperty(
        "otel.instrumentation.aws-sdk.batch-send.message-creation-spans.enabled", "false");
    try {
      assertThat(
              MessagingConfig.isBatchSendMessageCreationSpansEnabled(
                  openTelemetry, "aws_sdk", true))
          .isFalse();
    } finally {
      System.clearProperty(
          "otel.instrumentation.aws-sdk.batch-send.message-creation-spans.enabled");
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
    when(batchSendMessageCreationSpansConfig(instrumentationConfig).getBoolean("enabled"))
        .thenReturn(null);
    when(batchSendMessageCreationSpansConfig(messagingConfig).getBoolean("enabled"))
        .thenReturn(null);
    when(messagingConfig.get("headers/development").getScalarList("included", String.class))
        .thenReturn(null);
    when(messagingConfig.get("headers/development").getScalarList("excluded", String.class))
        .thenReturn(null);
    when(messagingConfig.getScalarList("capture_headers/development", String.class))
        .thenReturn(null);
    return openTelemetry;
  }

  private static Stream<Arguments> batchSendMessageCreationSpansCases() {
    return Stream.of(
        argumentSet("default", null, null, true),
        argumentSet("common fallback", null, false, false),
        argumentSet("instrumentation true overrides common false", true, false, true),
        argumentSet("instrumentation false overrides common true", false, true, false));
  }

  private static DeclarativeConfigProperties batchSendMessageCreationSpansConfig(
      DeclarativeConfigProperties config) {
    return config.get("batch_send").get("message_creation_spans");
  }

  private static DeclarativeConfigProperties instrumentationConfig(
      ExtendedOpenTelemetry openTelemetry) {
    return openTelemetry.getInstrumentationConfig("aws_sdk");
  }

  private static DeclarativeConfigProperties messagingConfig(ExtendedOpenTelemetry openTelemetry) {
    return openTelemetry.getInstrumentationConfig("common").get("messaging");
  }
}
