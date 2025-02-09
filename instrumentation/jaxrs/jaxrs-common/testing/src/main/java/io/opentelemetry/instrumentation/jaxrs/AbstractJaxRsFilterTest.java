/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jaxrs;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_REQUEST_METHOD;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_ROUTE;
import static io.opentelemetry.semconv.incubating.CodeIncubatingAttributes.CODE_FUNCTION;
import static io.opentelemetry.semconv.incubating.CodeIncubatingAttributes.CODE_NAMESPACE;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.api.internal.HttpConstants;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerUsingTest;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@SuppressWarnings("deprecation") // using deprecated semconv
public abstract class AbstractJaxRsFilterTest<SERVER> extends AbstractHttpServerUsingTest<SERVER> {

  protected static class TestResponse {
    private final String text;
    private final int status;

    public TestResponse(String text, int status) {
      this.text = text;
      this.status = status;
    }
  }

  protected abstract TestResponse makeRequest(String url) throws Exception;

  private TestResponse runRequest(String resource) throws Exception {
    if (runsOnServer()) {
      return makeRequest(resource);
    }
    // start a trace because the test doesn't go through any servlet or other instrumentation.
    return testing().runWithHttpServerSpan(() -> makeRequest(resource));
  }

  protected boolean testAbortPrematch() {
    return true;
  }

  protected boolean runsOnServer() {
    return false;
  }

  protected String defaultServerRoute() {
    return null;
  }

  protected abstract void setAbortStatus(boolean abortNormal, boolean abortPrematch);

  static Stream<Arguments> requests() {
    return Stream.of(
        Arguments.of(
            "/test/hello/bob", false, false, "/test/hello/{name}", "Test1.hello", "Test1 bob!"),
        Arguments.of(
            "/test2/hello/bob", false, false, "/test2/hello/{name}", "Test2.hello", "Test2 bob!"),
        Arguments.of(
            "/test3/hi/bob", false, false, "/test3/hi/{name}", "Test3.hello", "Test3 bob!"),
        // Resteasy and Jersey give different resource class names for just the below case
        // Resteasy returns "SubResource.class"
        // Jersey returns "Test1.class
        // Arguments.of("/test/hello/bob", true, false, "/test/hello/{name}", "Test1.hello",
        // "Aborted"),
        Arguments.of(
            "/test2/hello/bob", true, false, "/test2/hello/{name}", "Test2.hello", "Aborted"),
        Arguments.of("/test3/hi/bob", true, false, "/test3/hi/{name}", "Test3.hello", "Aborted"),
        Arguments.of(
            "/test/hello/bob",
            false,
            true,
            null,
            "PrematchRequestFilter.filter",
            "Aborted Prematch"),
        Arguments.of(
            "/test2/hello/bob",
            false,
            true,
            null,
            "PrematchRequestFilter.filter",
            "Aborted Prematch"),
        Arguments.of(
            "/test3/hi/bob",
            false,
            true,
            null,
            "PrematchRequestFilter.filter",
            "Aborted Prematch"));
  }

  @ParameterizedTest
  @MethodSource("requests")
  void request(
      String resource,
      boolean abortNormal,
      boolean abortPrematch,
      String route,
      String controllerName,
      String expectedResponse)
      throws Exception {
    Assumptions.assumeTrue(!abortPrematch || testAbortPrematch());

    setAbortStatus(abortNormal, abortPrematch);
    boolean abort = abortNormal || abortPrematch;

    TestResponse response = runRequest(resource);
    assertThat(response.text).isEqualTo(expectedResponse);

    assertThat(response.status)
        .isEqualTo(
            abort
                ? 401 // Response.Status.UNAUTHORIZED.statusCode
                : 200); // Response.Status.OK.statusCode

    String serverRoute = route != null ? route : defaultServerRoute();
    String method = runsOnServer() ? "POST" : "GET";
    String expectedServerSpanName = serverRoute == null ? method : method + " " + serverRoute;
    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> {
                      span.hasName(expectedServerSpanName).hasKind(SpanKind.SERVER);
                      if (runsOnServer() && abortNormal) {
                        span.hasStatus(StatusData.unset());
                      }
                    },
                    span -> {
                      span.hasName(controllerName).hasParent(trace.getSpan(0));
                      if (abortPrematch) {
                        span.hasAttributesSatisfyingExactly(
                            satisfies(
                                CODE_NAMESPACE,
                                name -> name.endsWith("JaxRsFilterTest$PrematchRequestFilter")),
                            equalTo(CODE_FUNCTION, "filter"));
                      } else {
                        span.hasAttributesSatisfyingExactly(
                            satisfies(CODE_NAMESPACE, name -> name.contains("Resource$Test")),
                            equalTo(CODE_FUNCTION, "hello"));
                      }
                    }));
  }

  @Test
  void nestedCall() throws Exception {
    setAbortStatus(false, false);

    TestResponse response = runRequest("/test3/nested");
    assertThat(response.status).isEqualTo(200); // Response.Status.OK.statusCode
    assertThat(response.text).isEqualTo("Test3 nested!");

    String method = runsOnServer() ? "POST" : "GET";

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> {
                      span.hasName(method + " /test3/nested")
                          .hasKind(SpanKind.SERVER)
                          .hasNoParent();
                      if (!runsOnServer()) {
                        span.hasAttributesSatisfyingExactly(
                            equalTo(HTTP_REQUEST_METHOD, method),
                            equalTo(HTTP_ROUTE, "/test3/nested"),
                            equalTo(ERROR_TYPE, HttpConstants._OTHER));
                      }
                    },
                    span ->
                        span.hasName("Test3.nested")
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfyingExactly(
                                satisfies(CODE_NAMESPACE, name -> name.contains("Resource$Test")),
                                equalTo(CODE_FUNCTION, "nested"))));
  }
}
