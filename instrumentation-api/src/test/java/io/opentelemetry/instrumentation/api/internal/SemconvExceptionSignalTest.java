/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import static io.opentelemetry.instrumentation.api.internal.SemconvExceptionSignal.isExceptionAsLogsOptedIn;
import static io.opentelemetry.instrumentation.api.internal.SemconvExceptionSignal.shouldEmitLogs;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor;
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class SemconvExceptionSignalTest {

  private static final OpenTelemetry withLoggerProvider =
      OpenTelemetrySdk.builder()
          .setLoggerProvider(
              SdkLoggerProvider.builder()
                  .addLogRecordProcessor(
                      SimpleLogRecordProcessor.create(InMemoryLogRecordExporter.create()))
                  .build())
          .build();

  // an SDK is installed, but without a log record processor nothing it is handed is ever exported
  private static final OpenTelemetry withoutLogRecordProcessor =
      OpenTelemetrySdk.builder().setLoggerProvider(SdkLoggerProvider.builder().build()).build();

  @ParameterizedTest
  @ValueSource(strings = {"logs", "logs/dup"})
  void optsInOnRecognizedValues(String value) {
    // not the same string instance the literal is, as the value comes from configuration
    assertThat(isExceptionAsLogsOptedIn(String.valueOf(value.toCharArray()))).isTrue();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"span-events", "LOGS"})
  void doesNotOptInOnUnrecognizedValues(String value) {
    assertThat(isExceptionAsLogsOptedIn(value)).isFalse();
  }

  @ParameterizedTest
  @ValueSource(strings = {"logs", "logs/dup"})
  void emitsLogsWhenALoggerProviderIsRegistered(String value) {
    assertThat(shouldEmitLogs(/* exceptionAsLogsOptedIn= */ true, value, withLoggerProvider))
        .isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {"logs", "logs/dup"})
  void doesNotEmitLogsWithoutALoggerProvider(String value) {
    // an exception log record goes nowhere without a LoggerProvider, so the opt-in cannot be
    // honored
    assertThat(shouldEmitLogs(/* exceptionAsLogsOptedIn= */ true, value, OpenTelemetry.noop()))
        .isFalse();
  }

  @ParameterizedTest
  @ValueSource(strings = {"logs", "logs/dup"})
  void doesNotEmitLogsWithoutALogRecordProcessor(String value) {
    // an SDK that drops every log record handed to it cannot honor the opt-in either, which is what
    // a bare identity comparison against LoggerProvider.noop() misses
    assertThat(shouldEmitLogs(/* exceptionAsLogsOptedIn= */ true, value, withoutLogRecordProcessor))
        .isFalse();
  }

  @Test
  void doesNotEmitLogsWithoutAnOptIn() {
    assertThat(shouldEmitLogs(/* exceptionAsLogsOptedIn= */ false, null, withLoggerProvider))
        .isFalse();
    assertThat(shouldEmitLogs(/* exceptionAsLogsOptedIn= */ false, null, OpenTelemetry.noop()))
        .isFalse();
  }
}
