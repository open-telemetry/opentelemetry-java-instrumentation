/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.webflux.v5_0.server.base;

import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.EXCEPTION;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.NOT_FOUND;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_MESSAGE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_STACKTRACE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_TYPE;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.api.internal.HttpConstants;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions;
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.trace.data.StatusData;

public abstract class HandlerSpringWebFluxServerTest extends SpringWebFluxServerTest {

  @Override
  protected SpanDataAssert assertHandlerSpan(
      SpanDataAssert span, String method, ServerEndpoint endpoint) {
    String handlerSpanName = ServerTestRouteFactory.class.getSimpleName() + "$$Lambda.handle";
    if (endpoint == NOT_FOUND) {
      handlerSpanName = "ResourceWebHandler.handle";
    }
    span.hasName(handlerSpanName).hasKind(SpanKind.INTERNAL);
    if (endpoint == EXCEPTION) {
      span.hasStatus(StatusData.error());
      span.hasEventsSatisfyingExactly(
          event ->
              event
                  .hasName("exception")
                  .hasAttributesSatisfyingExactly(
                      equalTo(EXCEPTION_TYPE, "java.lang.IllegalStateException"),
                      equalTo(EXCEPTION_MESSAGE, EXCEPTION.getBody()),
                      satisfies(EXCEPTION_STACKTRACE, val -> val.isInstanceOf(String.class))));
    } else if (endpoint == NOT_FOUND) {
      span.hasStatus(StatusData.error());
      if (Boolean.getBoolean("testLatestDeps")) {
        span.hasEventsSatisfyingExactly(
            event ->
                event
                    .hasName("exception")
                    .hasAttributesSatisfyingExactly(
                        equalTo(
                            EXCEPTION_TYPE,
                            "org.springframework.web.reactive.resource.NoResourceFoundException"),
                        equalTo(
                            EXCEPTION_MESSAGE, "404 NOT_FOUND \"No static resource notFound.\""),
                        satisfies(EXCEPTION_STACKTRACE, val -> val.isInstanceOf(String.class))));
      } else {
        span.hasEventsSatisfyingExactly(
            event ->
                event
                    .hasName("exception")
                    .hasAttributesSatisfyingExactly(
                        equalTo(
                            EXCEPTION_TYPE,
                            "org.springframework.web.server.ResponseStatusException"),
                        equalTo(EXCEPTION_MESSAGE, "Response status 404"),
                        satisfies(EXCEPTION_STACKTRACE, val -> val.isInstanceOf(String.class))));
      }
    }
    return span;
  }

  @Override
  protected void configure(HttpServerTestOptions options) {
    super.configure(options);
    // TODO (trask) it seems like in this case ideally the controller span (which ends when the
    // Mono that the controller returns completes) should end before the server span (which needs
    // the result of the Mono)
    options.setVerifyServerSpanEndTime(false);

    options.setResponseCodeOnNonStandardHttpMethod(404);
  }

  @Override
  public String expectedHttpRoute(ServerEndpoint endpoint, String method) {
    if (HttpConstants._OTHER.equals(method)) {
      return getContextPath() + "/**";
    }
    return super.expectedHttpRoute(endpoint, method);
  }
}
