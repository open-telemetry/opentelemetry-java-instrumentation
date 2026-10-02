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
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.api.incubator.config.internal.SelectorConfig;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

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

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void deprecatedSelectorWarnsOncePerAppliedLeaf(boolean common) throws Exception {
    ExtendedOpenTelemetry openTelemetry = mockOpenTelemetry();
    DeclarativeConfigProperties deprecatedConfig =
        common ? messagingConfig(openTelemetry) : deprecatedMessagingConfig(openTelemetry);
    when(deprecatedConfig.get("headers/development").getScalarList("included", String.class))
        .thenReturn(singletonList("*"));
    when(deprecatedConfig.get("headers/development").getScalarList("excluded", String.class))
        .thenReturn(singletonList("secret"));
    TestHandler handler = new TestHandler();
    Logger logger = Logger.getLogger(MessagingConfig.class.getName());
    clearDeprecatedWarnings();
    logger.addHandler(handler);
    try {
      IncludeExclude headers = MessagingConfig.getHeaders(openTelemetry);
      MessagingConfig.getHeaders(openTelemetry);

      assertThat(headers.matches("public")).isTrue();
      assertThat(headers.matches("secret")).isFalse();
      assertThat(handler.records).hasSize(2);
      assertThat(handler.records.get(0).getMessage())
          .contains(
              common
                  ? "otel.instrumentation.common.messaging.experimental.headers.included"
                  : "otel.instrumentation.messaging.experimental.headers.included",
              "otel.instrumentation.common.messaging.headers.included",
              "will be removed in 3.0");
      assertThat(handler.records.get(1).getMessage())
          .contains(
              common
                  ? "otel.instrumentation.common.messaging.experimental.headers.excluded"
                  : "otel.instrumentation.messaging.experimental.headers.excluded",
              "otel.instrumentation.common.messaging.headers.excluded",
              "will be removed in 3.0");

      when(messagingConfig(openTelemetry).get("headers").getScalarList("included", String.class))
          .thenReturn(emptyList());
      when(messagingConfig(openTelemetry).get("headers").getScalarList("excluded", String.class))
          .thenReturn(singletonList("stable-secret"));
      headers = MessagingConfig.getHeaders(openTelemetry);
      assertThat(headers.matches("stable-secret")).isFalse();
      assertThat(headers.matches("secret")).isTrue();
      assertThat(handler.records).hasSize(2);
    } finally {
      logger.removeHandler(handler);
      clearDeprecatedWarnings();
    }
  }

  @Test
  void stableLeavesOverrideDeprecatedLeavesWithoutWarnings() throws Exception {
    ExtendedOpenTelemetry openTelemetry = mockOpenTelemetry();
    when(messagingConfig(openTelemetry).get("headers").getScalarList("included", String.class))
        .thenReturn(singletonList("*"));
    when(messagingConfig(openTelemetry).get("headers").getScalarList("excluded", String.class))
        .thenReturn(singletonList("stable-secret"));
    when(messagingConfig(openTelemetry)
            .get("headers/development")
            .getScalarList("included", String.class))
        .thenReturn(singletonList("deprecated-common"));
    when(messagingConfig(openTelemetry)
            .get("headers/development")
            .getScalarList("excluded", String.class))
        .thenReturn(singletonList("deprecated-common-secret"));
    when(deprecatedMessagingConfig(openTelemetry)
            .get("headers/development")
            .getScalarList("included", String.class))
        .thenReturn(singletonList("deprecated"));
    when(deprecatedMessagingConfig(openTelemetry)
            .get("headers/development")
            .getScalarList("excluded", String.class))
        .thenReturn(singletonList("deprecated-secret"));
    TestHandler handler = new TestHandler();
    Logger logger = Logger.getLogger(MessagingConfig.class.getName());
    clearDeprecatedWarnings();
    logger.addHandler(handler);
    try {
      IncludeExclude headers = MessagingConfig.getHeaders(openTelemetry);
      assertThat(headers.matches("stable-secret")).isFalse();
      assertThat(headers.matches("deprecated-secret")).isTrue();
      assertThat(headers.matches("deprecated-common-secret")).isTrue();
      assertThat(handler.records).isEmpty();
    } finally {
      logger.removeHandler(handler);
      clearDeprecatedWarnings();
    }
  }

  @Test
  void emptyStableSelectorOverridesDeprecatedSelectors() {
    ExtendedOpenTelemetry openTelemetry = mockOpenTelemetry();
    when(messagingConfig(openTelemetry).get("headers").getScalarList("included", String.class))
        .thenReturn(emptyList());
    when(messagingConfig(openTelemetry)
            .get("headers/development")
            .getScalarList("included", String.class))
        .thenReturn(singletonList("deprecated-common"));
    when(deprecatedMessagingConfig(openTelemetry)
            .get("headers/development")
            .getScalarList("included", String.class))
        .thenReturn(singletonList("deprecated"));
    when(messagingConfig(openTelemetry).getScalarList("capture_headers/development", String.class))
        .thenReturn(singletonList("deprecated-capture"));

    assertThat(MessagingConfig.getHeaders(openTelemetry).isEmpty()).isTrue();
  }

  @Test
  void v3PreviewIgnoresDeprecatedSelectorsWithoutWarnings() throws Exception {
    ExtendedOpenTelemetry openTelemetry = mockOpenTelemetry();
    when(openTelemetry.getInstrumentationConfig("common").getBoolean("v3_preview"))
        .thenReturn(true);
    DeclarativeConfigProperties deprecatedConfig = deprecatedMessagingConfig(openTelemetry);
    when(deprecatedConfig.get("headers/development").getScalarList("included", String.class))
        .thenReturn(singletonList("older-alias"));
    when(deprecatedConfig.get("headers/development").getScalarList("excluded", String.class))
        .thenReturn(singletonList("older-exclusion"));
    DeclarativeConfigProperties messaging = messagingConfig(openTelemetry);
    when(messaging.get("headers/development").getScalarList("included", String.class))
        .thenReturn(singletonList("common-alias"));
    when(messaging.get("headers/development").getScalarList("excluded", String.class))
        .thenReturn(singletonList("common-exclusion"));
    when(messaging.getScalarList("capture_headers/development", String.class))
        .thenReturn(singletonList("older-capture"));
    clearInvocations(deprecatedConfig, messaging);
    TestHandler handler = new TestHandler();
    Logger logger = Logger.getLogger(MessagingConfig.class.getName());
    Logger selectorLogger = Logger.getLogger(SelectorConfig.class.getName());
    clearDeprecatedWarnings();
    logger.addHandler(handler);
    selectorLogger.addHandler(handler);
    try {
      assertThat(MessagingConfig.getHeaders(openTelemetry).isEmpty()).isTrue();
      verify(deprecatedConfig, never()).get("headers/development");
      verify(messaging, never()).get("headers/development");
      verify(messaging, never()).getScalarList("capture_headers/development", String.class);
      assertThat(handler.records).isEmpty();
    } finally {
      selectorLogger.removeHandler(handler);
      logger.removeHandler(handler);
      clearDeprecatedWarnings();
    }
  }

  @Test
  void v3PreviewUsesStableSelectorWithoutDeprecatedExclusions() {
    ExtendedOpenTelemetry openTelemetry = mockOpenTelemetry();
    when(openTelemetry.getInstrumentationConfig("common").getBoolean("v3_preview"))
        .thenReturn(true);
    when(messagingConfig(openTelemetry).get("headers").getScalarList("included", String.class))
        .thenReturn(singletonList("Test-*"));
    when(messagingConfig(openTelemetry).get("headers").getScalarList("excluded", String.class))
        .thenReturn(singletonList("Test-secret"));
    when(deprecatedMessagingConfig(openTelemetry)
            .get("headers/development")
            .getScalarList("excluded", String.class))
        .thenReturn(singletonList("Test-public"));

    IncludeExclude headers = MessagingConfig.getHeaders(openTelemetry);

    assertThat(headers.matches("Test-public")).isTrue();
    assertThat(headers.matches("test-public")).isFalse();
    assertThat(headers.matches("Test-secret")).isFalse();
  }

  @Test
  void readsDeprecatedCommonSelector() {
    ExtendedOpenTelemetry openTelemetry = mockOpenTelemetry();
    when(messagingConfig(openTelemetry)
            .get("headers/development")
            .getScalarList("included", String.class))
        .thenReturn(singletonList("deprecated"));
    when(messagingConfig(openTelemetry)
            .get("headers/development")
            .getScalarList("excluded", String.class))
        .thenReturn(singletonList("secret"));
    IncludeExclude headers = MessagingConfig.getHeaders(openTelemetry);

    assertThat(headers.getIncluded()).containsExactly("deprecated");
    assertThat(headers.getExcluded()).containsExactly("secret");
  }

  @Test
  void commonDeprecatedLeavesOverrideOlderLeavesIndependently() throws Exception {
    ExtendedOpenTelemetry openTelemetry = mockOpenTelemetry();
    when(messagingConfig(openTelemetry)
            .get("headers/development")
            .getScalarList("included", String.class))
        .thenReturn(singletonList("*"));
    when(deprecatedMessagingConfig(openTelemetry)
            .get("headers/development")
            .getScalarList("included", String.class))
        .thenReturn(singletonList("older"));
    when(deprecatedMessagingConfig(openTelemetry)
            .get("headers/development")
            .getScalarList("excluded", String.class))
        .thenReturn(singletonList("secret"));
    TestHandler handler = new TestHandler();
    Logger logger = Logger.getLogger(MessagingConfig.class.getName());
    clearDeprecatedWarnings();
    logger.addHandler(handler);
    try {
      IncludeExclude headers = MessagingConfig.getHeaders(openTelemetry);
      MessagingConfig.getHeaders(openTelemetry);

      assertThat(headers.matches("public")).isTrue();
      assertThat(headers.matches("secret")).isFalse();
      assertThat(handler.records).hasSize(2);
      assertThat(handler.records.get(0).getMessage())
          .contains("otel.instrumentation.common.messaging.experimental.headers.included");
      assertThat(handler.records.get(1).getMessage())
          .contains("otel.instrumentation.messaging.experimental.headers.excluded");
    } finally {
      logger.removeHandler(handler);
      clearDeprecatedWarnings();
    }
  }

  @Test
  void flatCommonDeprecatedLeavesOverrideOlderLeavesIndependently() throws Exception {
    OpenTelemetry openTelemetry = OpenTelemetry.noop();
    String commonIncluded = "otel.instrumentation.common.messaging.experimental.headers.included";
    String commonExcluded = "otel.instrumentation.common.messaging.experimental.headers.excluded";
    String olderIncluded = "otel.instrumentation.messaging.experimental.headers.included";
    String olderExcluded = "otel.instrumentation.messaging.experimental.headers.excluded";
    String stableIncluded = "otel.instrumentation.common.messaging.headers.included";
    System.setProperty(commonIncluded, "Test-*");
    System.setProperty(commonExcluded, "Test-secret");
    System.setProperty(olderIncluded, "older");
    System.setProperty(olderExcluded, "Test-public");
    TestHandler handler = new TestHandler();
    Logger logger = Logger.getLogger(MessagingConfig.class.getName());
    clearDeprecatedWarnings();
    logger.addHandler(handler);
    try {
      assertThat(MessagingConfig.getHeaders(openTelemetry, false).isEmpty()).isTrue();
      IncludeExclude headers = MessagingConfig.getHeaders(openTelemetry, true);
      MessagingConfig.getHeaders(openTelemetry, true);

      assertThat(headers.matches("Test-public")).isTrue();
      assertThat(headers.matches("test-public")).isFalse();
      assertThat(headers.matches("Test-secret")).isFalse();
      assertThat(handler.records).hasSize(2);
      assertThat(handler.records.get(0).getMessage()).contains(commonIncluded, stableIncluded);
      assertThat(handler.records.get(1).getMessage())
          .contains(commonExcluded, "otel.instrumentation.common.messaging.headers.excluded");

      System.setProperty(stableIncluded, "*");
      System.clearProperty(commonExcluded);
      headers = MessagingConfig.getHeaders(openTelemetry, true);
      assertThat(headers.getIncluded()).containsExactly("*");
      assertThat(headers.getExcluded()).containsExactly("Test-public");
      assertThat(handler.records).hasSize(3);
      assertThat(handler.records.get(2).getMessage()).contains(olderExcluded);
    } finally {
      logger.removeHandler(handler);
      clearDeprecatedWarnings();
      System.clearProperty(commonIncluded);
      System.clearProperty(commonExcluded);
      System.clearProperty(olderIncluded);
      System.clearProperty(olderExcluded);
      System.clearProperty(stableIncluded);
    }
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
  void absentSelectorCapturesNothing() {
    assertThat(MessagingConfig.getHeaders(mockOpenTelemetry()).isEmpty()).isTrue();
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
    String deprecatedExcluded = "otel.instrumentation.messaging.experimental.headers.excluded";
    System.setProperty(stableIncluded, "*");
    System.setProperty(deprecatedExcluded, "deprecated-excluded");
    try {
      IncludeExclude headers = MessagingConfig.getHeaders(openTelemetry, true);
      assertThat(headers.getIncluded()).containsExactly("*");
      assertThat(headers.getExcluded()).containsExactly("deprecated-excluded");
    } finally {
      System.clearProperty(stableIncluded);
      System.clearProperty(deprecatedExcluded);
    }
  }

  @Test
  void readsDeprecatedHeadersSystemPropertyOutsidePreview() {
    ExtendedOpenTelemetry openTelemetry = mockOpenTelemetry();
    String property = "otel.instrumentation.messaging.experimental.headers.included";
    System.setProperty(property, "from-deprecated-prop");
    try {
      assertThat(MessagingConfig.getHeaders(openTelemetry, true).getIncluded())
          .containsExactly("from-deprecated-prop");
    } finally {
      System.clearProperty(property);
    }
  }

  @Test
  void v3PreviewIgnoresDeprecatedFlatHeadersAndUsesStableFlatSelector() throws Exception {
    ExtendedOpenTelemetry openTelemetry = mockOpenTelemetry();
    String preview = "otel.instrumentation.common.v3-preview";
    String stable = "otel.instrumentation.common.messaging.headers.included";
    String deprecatedIncluded = "otel.instrumentation.messaging.experimental.headers.included";
    String deprecatedExcluded = "otel.instrumentation.messaging.experimental.headers.excluded";
    String deprecatedCapture = "otel.instrumentation.messaging.experimental.capture-headers";
    String deprecatedCommonIncluded =
        "otel.instrumentation.common.messaging.experimental.headers.included";
    String deprecatedCommonExcluded =
        "otel.instrumentation.common.messaging.experimental.headers.excluded";
    System.setProperty(preview, "true");
    System.setProperty(stable, "Test-*");
    System.setProperty(deprecatedIncluded, "older-alias");
    System.setProperty(deprecatedExcluded, "Test-public");
    System.setProperty(deprecatedCapture, "older-capture");
    System.setProperty(deprecatedCommonIncluded, "common-alias");
    System.setProperty(deprecatedCommonExcluded, "Test-public");
    TestHandler handler = new TestHandler();
    Logger logger = Logger.getLogger(MessagingConfig.class.getName());
    Logger selectorLogger = Logger.getLogger(SelectorConfig.class.getName());
    clearDeprecatedWarnings();
    logger.addHandler(handler);
    selectorLogger.addHandler(handler);
    try {
      IncludeExclude headers = MessagingConfig.getHeaders(openTelemetry, true);
      assertThat(headers.matches("Test-public")).isTrue();
      assertThat(headers.matches("test-public")).isFalse();
      System.clearProperty(stable);
      assertThat(MessagingConfig.getHeaders(openTelemetry, true).isEmpty()).isTrue();
      assertThat(handler.records).isEmpty();
    } finally {
      selectorLogger.removeHandler(handler);
      logger.removeHandler(handler);
      clearDeprecatedWarnings();
      System.clearProperty(preview);
      System.clearProperty(stable);
      System.clearProperty(deprecatedIncluded);
      System.clearProperty(deprecatedExcluded);
      System.clearProperty(deprecatedCapture);
      System.clearProperty(deprecatedCommonIncluded);
      System.clearProperty(deprecatedCommonExcluded);
    }
  }

  @Test
  void v3PreviewIgnoresDeprecatedCaptureHeadersWithoutStableSelector() {
    ExtendedOpenTelemetry openTelemetry = mockOpenTelemetry();
    when(messagingConfig(openTelemetry).getScalarList("capture_headers/development", String.class))
        .thenReturn(singletonList("deprecated"));
    when(openTelemetry.getInstrumentationConfig("common").getBoolean("v3_preview"))
        .thenReturn(true);

    assertThat(MessagingConfig.getHeaders(openTelemetry).isEmpty()).isTrue();
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
    String replacementProperty = "otel.instrumentation.common.messaging.headers.included";
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
  void emptyReplacementHeadersSystemPropertyFallsBackToDeprecatedProperty() {
    ExtendedOpenTelemetry openTelemetry = mockOpenTelemetry();
    String replacementProperty = "otel.instrumentation.common.messaging.headers.included";
    String deprecatedProperty = "otel.instrumentation.messaging.experimental.headers.included";
    System.setProperty(replacementProperty, "");
    System.setProperty(deprecatedProperty, "deprecated");
    try {
      assertThat(MessagingConfig.getHeaders(openTelemetry, true).getIncluded())
          .containsExactly("deprecated");
    } finally {
      System.clearProperty(replacementProperty);
      System.clearProperty(deprecatedProperty);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"included", "excluded"})
  void emptyCommonDeprecatedFlatLeafFallsBackToOlderLeaf(String leaf) {
    String common = "otel.instrumentation.common.messaging.experimental.headers." + leaf;
    String older = "otel.instrumentation.messaging.experimental.headers." + leaf;
    System.setProperty(common, "");
    System.setProperty(older, "older");
    try {
      IncludeExclude headers = MessagingConfig.getHeaders(OpenTelemetry.noop(), true);

      assertThat(leaf.equals("included") ? headers.getIncluded() : headers.getExcluded())
          .containsExactly("older");
    } finally {
      System.clearProperty(common);
      System.clearProperty(older);
    }
  }

  @Test
  void replacementAndDeprecatedHeaderAliasesAreResolvedIndependently() {
    ExtendedOpenTelemetry openTelemetry = mockOpenTelemetry();
    when(messagingConfig(openTelemetry).get("headers").getScalarList("included", String.class))
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
    when(commonConfig.getBoolean("v3_preview")).thenReturn(null);
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
    when(messagingConfig.get("headers").getScalarList("included", String.class)).thenReturn(null);
    when(messagingConfig.get("headers").getScalarList("excluded", String.class)).thenReturn(null);
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

  private static void clearDeprecatedWarnings() throws Exception {
    Field warnedDeprecatedPropertiesField =
        MessagingConfig.class.getDeclaredField("warnedDeprecatedProperties");
    warnedDeprecatedPropertiesField.setAccessible(true);
    ((Set<?>) warnedDeprecatedPropertiesField.get(null)).clear();
  }

  private static final class TestHandler extends Handler {
    private final List<LogRecord> records = new ArrayList<>();

    @Override
    public void publish(LogRecord record) {
      records.add(record);
    }

    @Override
    public void flush() {}

    @Override
    public void close() {}
  }
}
