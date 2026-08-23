/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal;

import static io.opentelemetry.instrumentation.api.internal.SemconvExceptionSignal.emitExceptionAsLogs;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_MESSAGE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_STACKTRACE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_TYPE;

import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import java.util.List;
import java.util.function.Consumer;
import org.assertj.core.api.AbstractAssert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class MessagingExceptionEventExtractorsTest {

  @RegisterExtension
  static final OpenTelemetryExtension otelTesting = OpenTelemetryExtension.create();

  @Test
  void messagingCreateExceptionLog() {
    assertExceptionLog(
        MessagingExceptionEventExtractors::setMessagingCreateExceptionEventExtractor,
        "messaging.create.exception",
        Severity.WARN);
  }

  @Test
  void messagingSendExceptionLog() {
    assertExceptionLog(
        MessagingExceptionEventExtractors::setMessagingSendExceptionEventExtractor,
        "messaging.send.exception",
        Severity.WARN);
  }

  @Test
  void messagingReceiveExceptionLog() {
    assertExceptionLog(
        MessagingExceptionEventExtractors::setMessagingReceiveExceptionEventExtractor,
        "messaging.receive.exception",
        Severity.WARN);
  }

  @Test
  void messagingSettleExceptionLog() {
    assertExceptionLog(
        MessagingExceptionEventExtractors::setMessagingSettleExceptionEventExtractor,
        "messaging.settle.exception",
        Severity.WARN);
  }

  @Test
  void messagingProcessExceptionLog() {
    assertExceptionLog(
        MessagingExceptionEventExtractors::setMessagingProcessExceptionEventExtractor,
        "messaging.process.exception",
        Severity.ERROR);
  }

  private static void assertExceptionLog(
      Consumer<InstrumenterBuilder<String, String>> configure,
      String expectedEventName,
      Severity expectedSeverity) {
    InstrumenterBuilder<String, String> builder =
        Instrumenter.builder(otelTesting.getOpenTelemetry(), "test", unused -> "span");
    configure.accept(builder);
    Instrumenter<String, String> instrumenter = builder.buildInstrumenter();

    Context context = instrumenter.start(Context.root(), "request");
    IllegalStateException error = new IllegalStateException("test");
    instrumenter.end(context, "request", "response", error);

    List<LogRecordData> logs = otelTesting.getLogRecords();
    if (emitExceptionAsLogs()) {
      assertThat(logs).hasSize(1);
      assertThat(logs.get(0))
          .hasSeverity(expectedSeverity)
          .hasEventName(expectedEventName)
          .hasAttributesSatisfyingExactly(
              equalTo(EXCEPTION_TYPE, "java.lang.IllegalStateException"),
              equalTo(EXCEPTION_MESSAGE, "test"),
              satisfies(EXCEPTION_STACKTRACE, AbstractAssert::isNotNull));
    } else {
      assertThat(logs).isEmpty();
    }
  }
}
