/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.armeria.v1_3;

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
import java.lang.reflect.Method;
import java.util.concurrent.CompletionException;
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

  private static final Method RESPONSE_EXCEPTION_WITH_CAUSE_FACTORY =
      findResponseExceptionWithCauseFactory();

  @RegisterExtension
  static final InstrumentationExtension testing = HttpServerInstrumentationExtension.forAgent();

  private final HttpResponseException wrappedResponseException =
      HttpResponseException.of(HttpResponse.of(HttpStatus.OK));

  @Override
  protected ServerBuilder configureServer(ServerBuilder sb) {
    sb.service(
        RESPONSE_EXCEPTION.getPath(),
        (ctx, req) -> {
          throw HttpResponseException.of(HttpResponse.of(HttpStatus.OK));
        });
    sb.service(
        RESPONSE_EXCEPTION_WITH_CAUSE.getPath(),
        (ctx, req) -> {
          throw responseExceptionWithCause();
        });
    sb.service(
        WRAPPED_RESPONSE_EXCEPTION.getPath(),
        (ctx, req) -> {
          throw new CompletionException(wrappedResponseException);
        });
    return sb;
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
                        .hasEventsSatisfyingExactly()));
  }

  @Test
  void responseExceptionWithCauseIsReported() {
    boolean usesHttp2 = getClass() == ArmeriaHttp2ServerTest.class;
    // HTTP/2 reads Armeria's request log; versions before 1.10 omit attached causes.
    // Detect the runtime API because testArmeria19 stays pinned in latest-dependency runs.
    boolean causeIsDropped = usesHttp2 && RESPONSE_EXCEPTION_WITH_CAUSE_FACTORY == null;
    AggregatedHttpResponse response =
        client.execute(request(RESPONSE_EXCEPTION_WITH_CAUSE, "GET")).aggregate().join();

    assertThat(response.status().code()).isEqualTo(200);
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasKind(SpanKind.SERVER)
                        .hasNoParent()
                        .hasStatus(causeIsDropped ? StatusData.unset() : StatusData.error())
                        .hasException(
                            causeIsDropped
                                ? null
                                : new IllegalStateException("response processing failure"))));
  }

  @Test
  void wrappedResponseExceptionIsReported() {
    AggregatedHttpResponse response =
        client.execute(request(WRAPPED_RESPONSE_EXCEPTION, "GET")).aggregate().join();

    assertThat(response.status().code()).isEqualTo(500);
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasKind(SpanKind.SERVER)
                        .hasNoParent()
                        .hasStatus(StatusData.error())
                        .hasException(wrappedResponseException)));
  }

  private static HttpResponseException responseExceptionWithCause() {
    IllegalStateException cause = new IllegalStateException("response processing failure");
    if (RESPONSE_EXCEPTION_WITH_CAUSE_FACTORY == null) {
      // Before Armeria 1.10.0, attach the cause with initCause().
      HttpResponseException response = HttpResponseException.of(HttpResponse.of(HttpStatus.OK));
      response.initCause(cause);
      return response;
    }
    try {
      return (HttpResponseException)
          RESPONSE_EXCEPTION_WITH_CAUSE_FACTORY.invoke(null, HttpResponse.of(HttpStatus.OK), cause);
    } catch (ReflectiveOperationException e) {
      throw new LinkageError(e.getMessage(), e);
    }
  }

  private static Method findResponseExceptionWithCauseFactory() {
    try {
      // HttpResponseException.of(HttpResponse, Throwable) is available in Armeria 1.10.0+.
      return HttpResponseException.class.getMethod("of", HttpResponse.class, Throwable.class);
    } catch (NoSuchMethodException ignored) {
      return null;
    }
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
