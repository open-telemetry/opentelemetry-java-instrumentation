/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.log4j.v2_16;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.opentelemetry.instrumentation.appender.api.LogEmitterProvider;
import io.opentelemetry.sdk.logs.SdkLogEmitterProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

class OpenTelemetryLog4jTest {

  @AfterAll
  static void afterAll() {
    OpenTelemetryLog4j.resetForTest();
  }

  @Test
  void registerAndInitialize() {
    OpenTelemetryAppender appender1 = mock(OpenTelemetryAppender.class);
    OpenTelemetryAppender appender2 = mock(OpenTelemetryAppender.class);
    OpenTelemetryLog4j.registerInstance(appender1);
    OpenTelemetryLog4j.registerInstance(appender2);

    OpenTelemetryLog4j.initialize(LogEmitterProvider.from(SdkLogEmitterProvider.builder().build()));

    verify(appender1).initialize(any());
    verify(appender2).initialize(any());

    assertThatCode(
            () ->
                OpenTelemetryLog4j.initialize(
                    LogEmitterProvider.from(SdkLogEmitterProvider.builder().build())))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("OpenTelemetryLog4j.initialize has already been called.");
  }
}
