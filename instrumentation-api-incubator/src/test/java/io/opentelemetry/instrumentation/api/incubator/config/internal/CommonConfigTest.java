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
import static org.mockito.Mockito.when;

import io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.api.incubator.log.LoggingContextConstants;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class CommonConfigTest {

  @Test
  void deprecatedLoggingKeyIgnoredInV3Preview() {
    ExtendedOpenTelemetry openTelemetry = mock(ExtendedOpenTelemetry.class);
    DeclarativeConfigProperties commonConfig =
        mock(DeclarativeConfigProperties.class, RETURNS_DEEP_STUBS);
    when(openTelemetry.getGeneralInstrumentationConfig())
        .thenReturn(DeclarativeConfigProperties.empty());
    when(openTelemetry.getInstrumentationConfig("common")).thenReturn(commonConfig);
    when(commonConfig.getBoolean("v3_preview", false)).thenReturn(true);
    when(commonConfig.get("http").getScalarList(eq("known_methods"), eq(String.class), anyList()))
        .thenReturn(new ArrayList<>());
    when(commonConfig.get("logging").getString("trace_id")).thenReturn("custom_trace_id");

    CommonConfig config = new CommonConfig(openTelemetry);

    assertThat(config.getTraceIdKey()).isEqualTo(LoggingContextConstants.TRACE_ID);
  }
}
