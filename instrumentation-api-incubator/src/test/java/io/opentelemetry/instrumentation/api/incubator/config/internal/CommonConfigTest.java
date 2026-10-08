/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.config.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.api.incubator.log.LoggingContextConstants;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class CommonConfigTest {

  @Test
  void loggingKeys() {
    ExtendedOpenTelemetry openTelemetry = mock(ExtendedOpenTelemetry.class);
    DeclarativeConfigProperties commonConfig =
        mock(DeclarativeConfigProperties.class, RETURNS_DEEP_STUBS);
    when(openTelemetry.getGeneralInstrumentationConfig())
        .thenReturn(DeclarativeConfigProperties.empty());
    when(openTelemetry.getInstrumentationConfig("common")).thenReturn(commonConfig);
    when(commonConfig.get("http").getScalarList(eq("known_methods"), eq(String.class), anyList()))
        .thenReturn(new ArrayList<>());
    when(commonConfig.get("logging").getString("trace_id")).thenReturn("legacy_trace_id");
    when(commonConfig.get("logging").getString("span_id")).thenReturn("legacy_span_id");
    when(commonConfig.get("logging").getString("trace_flags")).thenReturn("legacy_trace_flags");
    DeclarativeConfigProperties logging = commonConfig.get("logging");
    when(logging.getString("trace_id_key", LoggingContextConstants.TRACE_ID))
        .thenReturn(LoggingContextConstants.TRACE_ID);
    when(logging.getString("span_id_key", LoggingContextConstants.SPAN_ID))
        .thenReturn(LoggingContextConstants.SPAN_ID);
    when(logging.getString("trace_flags_key", LoggingContextConstants.TRACE_FLAGS))
        .thenReturn(LoggingContextConstants.TRACE_FLAGS);

    CommonConfig config = new CommonConfig(openTelemetry);

    assertThat(config.getTraceIdKey()).isEqualTo(LoggingContextConstants.TRACE_ID);
    assertThat(config.getSpanIdKey()).isEqualTo(LoggingContextConstants.SPAN_ID);
    assertThat(config.getTraceFlagsKey()).isEqualTo(LoggingContextConstants.TRACE_FLAGS);
    verify(logging, never()).getString("trace_id");
    verify(logging, never()).getString("span_id");
    verify(logging, never()).getString("trace_flags");

    when(logging.getString("trace_id_key", LoggingContextConstants.TRACE_ID))
        .thenReturn("supported_trace_id");
    when(logging.getString("span_id_key", LoggingContextConstants.SPAN_ID))
        .thenReturn("supported_span_id");
    when(logging.getString("trace_flags_key", LoggingContextConstants.TRACE_FLAGS))
        .thenReturn("supported_trace_flags");

    config = new CommonConfig(openTelemetry);

    assertThat(config.getTraceIdKey()).isEqualTo("supported_trace_id");
    assertThat(config.getSpanIdKey()).isEqualTo("supported_span_id");
    assertThat(config.getTraceFlagsKey()).isEqualTo("supported_trace_flags");
  }
}
