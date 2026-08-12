/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db.internal;

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
import org.assertj.core.api.AbstractAssert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class DbExceptionEventExtractorsTest {

  @RegisterExtension
  static final OpenTelemetryExtension otelTesting = OpenTelemetryExtension.create();

  @Test
  void dbClientExceptionLog() {
    InstrumenterBuilder<String, String> builder =
        Instrumenter.builder(otelTesting.getOpenTelemetry(), "test", unused -> "span");
    DbExceptionEventExtractors.setDbClientExceptionEventExtractor(builder);
    Instrumenter<String, String> instrumenter = builder.buildInstrumenter();

    Context context = instrumenter.start(Context.root(), "request");
    IllegalStateException error = new IllegalStateException("test");
    instrumenter.end(context, "request", "response", error);

    List<LogRecordData> logs = otelTesting.getLogRecords();
    if (emitExceptionAsLogs()) {
      assertThat(logs).hasSize(1);
      assertThat(logs.get(0))
          .hasSeverity(Severity.WARN)
          .hasEventName("db.client.operation.exception")
          .hasAttributesSatisfyingExactly(
              equalTo(EXCEPTION_TYPE, "java.lang.IllegalStateException"),
              equalTo(EXCEPTION_MESSAGE, "test"),
              satisfies(EXCEPTION_STACKTRACE, AbstractAssert::isNotNull));
    } else {
      assertThat(logs).isEmpty();
    }
  }
}
