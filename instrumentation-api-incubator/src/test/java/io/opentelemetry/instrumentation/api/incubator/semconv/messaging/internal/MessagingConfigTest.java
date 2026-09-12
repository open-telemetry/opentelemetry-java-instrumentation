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
    when(messagingConfig(openTelemetry)
            .get("headers/development")
            .getScalarList("included", String.class))
        .thenReturn(singletonList("Test-*"));

    assertThat(MessagingConfig.getHeaders(openTelemetry).getIncluded()).containsExactly("Test-*");
  }

  @Test
  void readsDeprecatedCaptureHeadersFromCommonMessagingConfig() {
    ExtendedOpenTelemetry openTelemetry = mockOpenTelemetry();
    when(messagingConfig(openTelemetry).getScalarList("capture_headers/development", String.class))
        .thenReturn(singletonList("deprecated"));

    assertThat(MessagingConfig.getHeaders(openTelemetry).getIncluded())
        .containsExactly("deprecated");
  }

  @Test
  void emptyDeprecatedHeadersFallBackToDeprecatedCaptureHeaders() {
    ExtendedOpenTelemetry openTelemetry = mockOpenTelemetry();
    when(deprecatedMessagingConfig(openTelemetry)
            .get("headers/development")
            .getScalarList("included", String.class))
        .thenReturn(emptyList());
    when(messagingConfig(openTelemetry).getScalarList("capture_headers/development", String.class))
        .thenReturn(singletonList("deprecated-capture"));

    assertThat(MessagingConfig.getHeaders(openTelemetry).getIncluded())
        .containsExactly("deprecated-capture");
  }

  @Test
  void emptySelectorFallsBackToDeprecatedCaptureHeaders() {
    ExtendedOpenTelemetry openTelemetry = mockOpenTelemetry();
    when(messagingConfig(openTelemetry)
            .get("headers/development")
            .getScalarList("included", String.class))
        .thenReturn(emptyList());
    when(messagingConfig(openTelemetry).getScalarList("capture_headers/development", String.class))
        .thenReturn(singletonList("deprecated-capture"));

    assertThat(MessagingConfig.getHeaders(openTelemetry).getIncluded())
        .containsExactly("deprecated-capture");
  }

  @Test
  void absentSelectorCapturesNothing() {
    assertThat(MessagingConfig.getHeaders(mockOpenTelemetry()).isEmpty()).isTrue();
  }

  @Test
  void systemPropertyFallbackIsOnlyUsedWhenEnabled() {
    ExtendedOpenTelemetry openTelemetry = mockOpenTelemetry();
    String property = "otel.instrumentation.common.messaging.experimental.headers.included";
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
  void readsDeprecatedHeadersSystemProperty() {
    ExtendedOpenTelemetry openTelemetry = mockOpenTelemetry();
    String property = "otel.instrumentation.messaging.experimental.headers.included";
    System.setProperty(property, "from-deprecated-prop");
    try {
      assertThat(MessagingConfig.getHeaders(openTelemetry, true).getIncluded())
          .containsExactly("from-deprecated-prop");

      when(openTelemetry.getInstrumentationConfig("common").getBoolean("v3_preview"))
          .thenReturn(true);
      assertThat(MessagingConfig.getHeaders(openTelemetry, true).getIncluded())
          .containsExactly("from-deprecated-prop");
    } finally {
      System.clearProperty(property);
    }
  }

  @Test
  void v3PreviewStillReadsDeprecatedCaptureHeaders() {
    ExtendedOpenTelemetry openTelemetry = mockOpenTelemetry();
    when(messagingConfig(openTelemetry).getScalarList("capture_headers/development", String.class))
        .thenReturn(singletonList("deprecated"));
    when(openTelemetry.getInstrumentationConfig("common").getBoolean("v3_preview"))
        .thenReturn(true);

    assertThat(MessagingConfig.getHeaders(openTelemetry).getIncluded())
        .containsExactly("deprecated");
  }

  @Test
  void readsDeprecatedCaptureHeadersSystemProperty() {
    ExtendedOpenTelemetry openTelemetry = mockOpenTelemetry();
    String property = "otel.instrumentation.messaging.experimental.capture-headers";
    System.setProperty(property, "deprecated");
    try {
      assertThat(MessagingConfig.getHeaders(openTelemetry, true).getIncluded())
          .containsExactly("deprecated");
    } finally {
      System.clearProperty(property);
    }
  }

  @Test
  void doesNotReadCommonCaptureHeadersSystemProperty() {
    ExtendedOpenTelemetry openTelemetry = mockOpenTelemetry();
    String property = "otel.instrumentation.common.messaging.experimental.capture-headers";
    System.setProperty(property, "not-supported");
    try {
      assertThat(MessagingConfig.getHeaders(openTelemetry, true).isEmpty()).isTrue();
    } finally {
      System.clearProperty(property);
    }
  }

  @Test
  void replacementHeadersSystemPropertyTakesPrecedenceOverDeprecatedProperty() {
    ExtendedOpenTelemetry openTelemetry = mockOpenTelemetry();
    String replacementProperty =
        "otel.instrumentation.common.messaging.experimental.headers.included";
    String deprecatedProperty = "otel.instrumentation.messaging.experimental.headers.included";
    System.setProperty(replacementProperty, "replacement");
    System.setProperty(deprecatedProperty, "deprecated");
    try {
      assertThat(MessagingConfig.getHeaders(openTelemetry, true).getIncluded())
          .containsExactly("replacement");
    } finally {
      System.clearProperty(replacementProperty);
      System.clearProperty(deprecatedProperty);
    }
  }

  @Test
  void replacementAndDeprecatedHeaderAliasesAreResolvedIndependently() {
    ExtendedOpenTelemetry openTelemetry = mockOpenTelemetry();
    when(messagingConfig(openTelemetry)
            .get("headers/development")
            .getScalarList("included", String.class))
        .thenReturn(singletonList("*"));
    when(deprecatedMessagingConfig(openTelemetry)
            .get("headers/development")
            .getScalarList("excluded", String.class))
        .thenReturn(singletonList("authorization"));

    IncludeExclude headers = MessagingConfig.getHeaders(openTelemetry);

    assertThat(headers.matches("x-test")).isTrue();
    assertThat(headers.matches("authorization")).isFalse();
  }

  @Test
  void resolvesReceiveTelemetryConfig() {
    ExtendedOpenTelemetry openTelemetry = mockOpenTelemetry();
    when(messagingConfig(openTelemetry).get("receive_telemetry/development").getBoolean("enabled"))
        .thenReturn(true);

    assertThat(MessagingConfig.isReceiveTelemetryEnabled(openTelemetry, false)).isTrue();
  }

  @Test
  void readsDeprecatedReceiveTelemetrySystemProperty() {
    ExtendedOpenTelemetry openTelemetry = mockOpenTelemetry();
    String property = "otel.instrumentation.messaging.experimental.receive-telemetry.enabled";
    System.setProperty(property, "true");
    try {
      assertThat(MessagingConfig.isReceiveTelemetryEnabled(openTelemetry, true)).isTrue();

      when(openTelemetry.getInstrumentationConfig("common").getBoolean("v3_preview"))
          .thenReturn(true);
      assertThat(MessagingConfig.isReceiveTelemetryEnabled(openTelemetry, true)).isTrue();
    } finally {
      System.clearProperty(property);
    }
  }

  @Test
  void replacementReceiveTelemetrySystemPropertyTakesPrecedenceOverDeprecatedProperty() {
    ExtendedOpenTelemetry openTelemetry = mockOpenTelemetry();
    String replacementProperty =
        "otel.instrumentation.common.messaging.experimental.receive-telemetry.enabled";
    String deprecatedProperty =
        "otel.instrumentation.messaging.experimental.receive-telemetry.enabled";
    System.setProperty(replacementProperty, "false");
    System.setProperty(deprecatedProperty, "true");
    try {
      assertThat(MessagingConfig.isReceiveTelemetryEnabled(openTelemetry, true)).isFalse();
    } finally {
      System.clearProperty(replacementProperty);
      System.clearProperty(deprecatedProperty);
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
    DeclarativeConfigProperties deprecatedMessagingConfig =
        mock(DeclarativeConfigProperties.class, RETURNS_DEEP_STUBS);
    when(openTelemetry.getInstrumentationConfig("messaging")).thenReturn(deprecatedMessagingConfig);
    DeclarativeConfigProperties messagingConfig = commonConfig.get("messaging");
    when(messagingConfig.get("receive_telemetry/development").getBoolean("enabled"))
        .thenReturn(null);
    when(messageCreateSpansConfig(instrumentationConfig).getBoolean("enabled")).thenReturn(null);
    when(messageCreateSpansConfig(messagingConfig).getBoolean("enabled")).thenReturn(null);
    when(messagingConfig.get("headers/development").getScalarList("included", String.class))
        .thenReturn(null);
    when(messagingConfig.get("headers/development").getScalarList("excluded", String.class))
        .thenReturn(null);
    when(messagingConfig.getScalarList("capture_headers/development", String.class))
        .thenReturn(null);
    when(deprecatedMessagingConfig.get("receive_telemetry/development").getBoolean("enabled"))
        .thenReturn(null);
    when(deprecatedMessagingConfig
            .get("headers/development")
            .getScalarList("included", String.class))
        .thenReturn(null);
    when(deprecatedMessagingConfig
            .get("headers/development")
            .getScalarList("excluded", String.class))
        .thenReturn(null);
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

  private static DeclarativeConfigProperties deprecatedMessagingConfig(
      ExtendedOpenTelemetry openTelemetry) {
    return openTelemetry.getInstrumentationConfig("messaging");
  }
}
