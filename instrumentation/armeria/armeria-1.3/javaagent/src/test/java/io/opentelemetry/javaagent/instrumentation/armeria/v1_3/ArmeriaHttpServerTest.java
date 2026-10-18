/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.armeria.v1_3;

import static io.opentelemetry.instrumentation.api.internal.SemconvExceptionSignal.emitExceptionAsLogs;
import static io.opentelemetry.instrumentation.api.internal.SemconvExceptionSignal.emitExceptionAsSpanEvents;
import static io.opentelemetry.instrumentation.testing.util.TestLatestDeps.testLatestDeps;
import static org.assertj.core.api.Assertions.assertThat;

import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.server.HttpResponseException;
import com.linecorp.armeria.server.ServerBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.armeria.v1_3.AbstractArmeriaHttpServerTest;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions;
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpResponse;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class ArmeriaHttpServerTest extends AbstractArmeriaHttpServerTest {

  private static final ServerEndpoint RESPONSE_EXCEPTION =
      new ServerEndpoint("RESPONSE_EXCEPTION", "response-exception", 200, "", false);
  private static final ServerEndpoint RESPONSE_EXCEPTION_WITH_CAUSE =
      new ServerEndpoint(
          "RESPONSE_EXCEPTION_WITH_CAUSE", "response-exception-with-cause", 200, "", false);
  private static final ServerEndpoint WRAPPED_RESPONSE_EXCEPTION =
      new ServerEndpoint(
          "WRAPPED_RESPONSE_EXCEPTION", "wrapped-response-exception", 500, "", false);

  @RegisterExtension
  static final InstrumentationExtension testing = HttpServerInstrumentationExtension.forAgent();

  @Override
  protected ServerBuilder configureServer(ServerBuilder sb) {
    sb.service(
        RESPONSE_EXCEPTION.getPath(),
        (ctx, req) -> {
          throw HttpResponseException.of(HttpResponse.of(HttpStatus.OK));
        });
    sb.service(
        WRAPPED_RESPONSE_EXCEPTION.getPath(),
        (ctx, req) -> {
          throw new CompletionException(HttpResponseException.of(HttpResponse.of(HttpStatus.OK)));
        });
    sb.service(
        RESPONSE_EXCEPTION_WITH_CAUSE.getPath(),
        (ctx, req) -> {
          HttpResponseException response = HttpResponseException.of(HttpResponse.of(HttpStatus.OK));
          response.initCause(new IllegalStateException("recovered failure"));
          throw response;
        });
    return sb;
  }

  @Test
  void wrappedResponseExceptionIsReported() {
    Assumptions.assumeTrue(
        getClass() == ArmeriaHttpServerTest.class,
        "The HTTP/2 fallback uses the library instrumenter instead of ServerDecorator");
    AggregatedHttpResponse response =
        client.execute(request(WRAPPED_RESPONSE_EXCEPTION, "GET")).aggregate().join();

    assertThat(response.status().code()).isEqualTo(500);
    HttpResponseException error = HttpResponseException.of(HttpResponse.of(HttpStatus.OK));
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasKind(SpanKind.SERVER)
                        .hasNoParent()
                        .hasStatus(StatusData.error())
                        .hasException(emitExceptionAsSpanEvents() ? error : null)));
    if (emitExceptionAsLogs()) {
      assertExceptionLogs(error, "http.server.request.exception");
    }
  }

  @Test
  void responseExceptionWithCauseIsReported() {
    // The older supported versions allow attaching a cause through Throwable.initCause().
    Assumptions.assumeFalse(testLatestDeps());
    Assumptions.assumeTrue(
        getClass() == ArmeriaHttpServerTest.class,
        "Old Armeria discards response-control causes before the HTTP/2 fallback sees them");
    AggregatedHttpResponse response =
        client.execute(request(RESPONSE_EXCEPTION_WITH_CAUSE, "GET")).aggregate().join();

    assertThat(response.status().code()).isEqualTo(200);
    IllegalStateException error = new IllegalStateException("recovered failure");
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasKind(SpanKind.SERVER)
                        .hasNoParent()
                        .hasStatus(StatusData.error())
                        .hasException(emitExceptionAsSpanEvents() ? error : null)));
    if (emitExceptionAsLogs()) {
      assertExceptionLogs(error, "http.server.request.exception");
    }
  }

  @Test
  void responseExceptionIsNotAnError() {
    AggregatedHttpResponse response =
        client.execute(request(RESPONSE_EXCEPTION, "GET")).aggregate().join();

    assertThat(response.status().code()).isEqualTo(200);
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasKind(SpanKind.SERVER)
                        .hasNoParent()
                        .hasStatus(StatusData.unset())
                        .hasException(null)));
    assertThat(testing.logRecords())
        .noneMatch(log -> "http.server.request.exception".equals(log.getEventName()));
  }

  @Override
  protected void configure(HttpServerTestOptions options) {
    super.configure(options);
    options.setHasResponseCustomizer(
        endpoint -> ServerEndpoint.NOT_FOUND != endpoint && ServerEndpoint.EXCEPTION != endpoint);
    options.setTestHttpPipelining(false);
    // span for non-standard request is created by netty instrumentation when not running latest
    // dep tests
    if (testLatestDeps()) {
      options.disableTestNonStandardHttpMethod();
    } else {
      options.setResponseCodeOnNonStandardHttpMethod(405);
    }
  }
}
