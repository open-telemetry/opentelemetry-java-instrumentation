/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.log4j.contextdata.v2_17.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.api.incubator.log.LoggingContextConstants;
import org.junit.jupiter.api.Test;

class ContextDataKeysTest {

  @Test
  void deprecatedLoggingKeyIgnoredInV3Preview() {
    ExtendedOpenTelemetry openTelemetry = mock(ExtendedOpenTelemetry.class);
    DeclarativeConfigProperties commonConfig =
        mock(DeclarativeConfigProperties.class, RETURNS_DEEP_STUBS);
    when(openTelemetry.getInstrumentationConfig("common")).thenReturn(commonConfig);
    when(commonConfig.getBoolean("v3_preview")).thenReturn(true);
    when(commonConfig.get("logging").getString("trace_id")).thenReturn("custom_trace_id");

    ContextDataKeys contextDataKeys = ContextDataKeys.create(openTelemetry);

    assertThat(contextDataKeys.getTraceIdKey()).isEqualTo(LoggingContextConstants.TRACE_ID);
  }
}
