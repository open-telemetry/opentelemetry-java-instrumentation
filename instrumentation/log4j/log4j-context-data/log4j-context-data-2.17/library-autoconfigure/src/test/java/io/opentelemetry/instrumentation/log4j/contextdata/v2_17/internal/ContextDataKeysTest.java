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
  void loggingKeys() {
    ExtendedOpenTelemetry openTelemetry = mock(ExtendedOpenTelemetry.class);
    DeclarativeConfigProperties commonConfig =
        mock(DeclarativeConfigProperties.class, RETURNS_DEEP_STUBS);
    when(openTelemetry.getInstrumentationConfig("common")).thenReturn(commonConfig);

    ContextDataKeys contextDataKeys = ContextDataKeys.create(openTelemetry);

    assertThat(contextDataKeys.getTraceIdKey()).isEqualTo(LoggingContextConstants.TRACE_ID);
    assertThat(contextDataKeys.getSpanIdKey()).isEqualTo(LoggingContextConstants.SPAN_ID);
    assertThat(contextDataKeys.getTraceFlagsKey()).isEqualTo(LoggingContextConstants.TRACE_FLAGS);
    DeclarativeConfigProperties logging = commonConfig.get("logging");

    when(logging.getString("trace_id_key")).thenReturn("supported_trace_id");
    when(logging.getString("span_id_key")).thenReturn("supported_span_id");
    when(logging.getString("trace_flags_key")).thenReturn("supported_trace_flags");

    contextDataKeys = ContextDataKeys.create(openTelemetry);

    assertThat(contextDataKeys.getTraceIdKey()).isEqualTo("supported_trace_id");
    assertThat(contextDataKeys.getSpanIdKey()).isEqualTo("supported_span_id");
    assertThat(contextDataKeys.getTraceFlagsKey()).isEqualTo("supported_trace_flags");
  }
}
