/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.junit.http;

import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.EXCEPTION;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_MESSAGE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_STACKTRACE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_TYPE;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.instrumentation.testing.InstrumentationTestRunner;
import io.opentelemetry.sdk.testing.logs.TestLogRecordData;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class HttpServerExceptionAssertionsTest {

  private static final SpanContext SERVER_CONTEXT =
      SpanContext.create(
          "00000000000000000000000000000001",
          "0000000000000001",
          TraceFlags.getSampled(),
          TraceState.getDefault());

  @Test
  void logExpectationIsIndependentOfServerSpanEvents() {
    HttpServerTestOptions options = new HttpServerTestOptions();
    options.setHasHandlerSpan(endpoint -> true);
    options.setHasExceptionOnServerSpan(endpoint -> false);

    assertThat(options.hasExceptionLog).isTrue();
    assertThat(options.exceptionLogOnHandlerSpan).isFalse();
    assertThat(options.hasExceptionOnServerSpan.test(EXCEPTION)).isFalse();

    options.setHasExceptionLog(false);
    options.setHasExceptionOnServerSpan(endpoint -> true);
    assertThat(options.hasExceptionLog).isFalse();
    assertThat(options.hasExceptionOnServerSpan.test(EXCEPTION)).isTrue();
  }

  @Test
  void matchingSpanContext() {
    fixture(SERVER_CONTEXT)
        .assertExceptionLogs(
            new IllegalStateException("test"), "http.server.request.exception", span());
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void selectsServerOrFrameworkHandler(boolean onHandlerSpan) {
    HttpServerTestOptions options = new HttpServerTestOptions();
    options.setHasHandlerSpan(endpoint -> true);
    options.setExceptionLogOnHandlerSpan(onHandlerSpan);
    SpanData server = mock(SpanData.class, "server");
    SpanData handler = mock(SpanData.class, "handler");
    SpanData controller = mock(SpanData.class, "controller");

    assertThat(
            AbstractHttpServerTest.getExceptionLogSpan(
                asList(server, handler, controller), options))
        .isSameAs(onHandlerSpan ? handler : server);
  }

  @Test
  void missingHandlerDoesNotFallBackToServerOrController() {
    HttpServerTestOptions options = new HttpServerTestOptions();
    options.setExceptionLogOnHandlerSpan(true);
    SpanData server = mock(SpanData.class, "server");
    SpanData controller = mock(SpanData.class, "controller");

    assertThatThrownBy(
            () -> AbstractHttpServerTest.getExceptionLogSpan(asList(server, controller), options))
        .isInstanceOf(AssertionError.class);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void rejectsDifferentSpanOrTraceFlags(boolean differentFlags) {
    SpanContext context =
        SpanContext.create(
            SERVER_CONTEXT.getTraceId(),
            differentFlags ? SERVER_CONTEXT.getSpanId() : "0000000000000002",
            differentFlags ? TraceFlags.getDefault() : SERVER_CONTEXT.getTraceFlags(),
            TraceState.getDefault());

    assertThatThrownBy(
            () ->
                fixture(context)
                    .assertExceptionLogs(
                        new IllegalStateException("test"), "http.server.request.exception", span()))
        .isInstanceOf(ConditionTimeoutException.class)
        .hasCauseInstanceOf(AssertionError.class);
  }

  private static AbstractHttpServerTest<Object> fixture(SpanContext context) {
    AbstractHttpServerTest<Object> fixture =
        new AbstractHttpServerTest<Object>() {
          @Override
          protected Object setupServer() {
            throw new UnsupportedOperationException();
          }

          @Override
          protected void stopServer(Object server) {}
        };
    fixture.testing = mock(InstrumentationTestRunner.class);
    when(fixture.testing.getExportedLogRecords())
        .thenReturn(
            singletonList(
                TestLogRecordData.builder()
                    .setSpanContext(context)
                    .setSeverity(Severity.ERROR)
                    .setEventName("http.server.request.exception")
                    .setAttributes(
                        Attributes.of(
                            EXCEPTION_TYPE, IllegalStateException.class.getName(),
                            EXCEPTION_MESSAGE, "test",
                            EXCEPTION_STACKTRACE, "stacktrace"))
                    .build()));
    return fixture;
  }

  private static SpanData span() {
    SpanData span = mock(SpanData.class);
    when(span.getSpanContext()).thenReturn(SERVER_CONTEXT);
    return span;
  }
}
