/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.internal;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import org.junit.jupiter.api.Test;

class ExperimentalConfigTest {

  @Test
  void readsMessagingHeaderSelector() {
    ExtendedOpenTelemetry openTelemetry = mockOpenTelemetry();
    DeclarativeConfigProperties messaging =
        openTelemetry.getInstrumentationConfig("common").get("messaging");
    when(messaging.get("headers/development").getScalarList("included", String.class))
        .thenReturn(asList("Test-*", "other"));
    when(messaging.get("headers/development").getScalarList("excluded", String.class))
        .thenReturn(singletonList("*-secret"));

    IncludeExclude headers = new ExperimentalConfig(openTelemetry).getMessagingHeaders();

    assertThat(headers.getIncluded()).containsExactly("Test-*", "other");
    assertThat(headers.getExcluded()).containsExactly("*-secret");
  }

  @Test
  void fallsBackToDeprecatedCaptureHeaders() {
    ExtendedOpenTelemetry openTelemetry = mockOpenTelemetry();
    when(openTelemetry
            .getInstrumentationConfig("common")
            .get("messaging")
            .getScalarList("capture_headers/development", String.class))
        .thenReturn(singletonList("deprecated"));

    IncludeExclude headers = new ExperimentalConfig(openTelemetry).getMessagingHeaders();

    assertThat(headers.getIncluded()).containsExactly("deprecated");
    assertThat(headers.getExcluded()).isEmpty();
  }

  @Test
  void absentConfigCapturesNothing() {
    IncludeExclude headers = new ExperimentalConfig(mockOpenTelemetry()).getMessagingHeaders();

    assertThat(headers.isEmpty()).isTrue();
  }

  private static ExtendedOpenTelemetry mockOpenTelemetry() {
    ExtendedOpenTelemetry openTelemetry = mock(ExtendedOpenTelemetry.class);
    DeclarativeConfigProperties commonConfig =
        mock(DeclarativeConfigProperties.class, RETURNS_DEEP_STUBS);
    when(openTelemetry.getInstrumentationConfig("common")).thenReturn(commonConfig);
    DeclarativeConfigProperties messaging = commonConfig.get("messaging");
    when(messaging.get("headers/development").getScalarList("included", String.class))
        .thenReturn(null);
    when(messaging.get("headers/development").getScalarList("excluded", String.class))
        .thenReturn(null);
    when(messaging.getScalarList("capture_headers/development", String.class)).thenReturn(null);
    return openTelemetry;
  }
}
