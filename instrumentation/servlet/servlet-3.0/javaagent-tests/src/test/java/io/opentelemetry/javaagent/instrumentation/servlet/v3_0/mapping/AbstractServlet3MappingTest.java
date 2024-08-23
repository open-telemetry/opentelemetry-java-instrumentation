/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v3_0.mapping;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerUsingTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpResponse;
import java.io.IOException;
import javax.servlet.Servlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

abstract class AbstractServlet3MappingTest<SERVER, CONTEXT>
    extends AbstractHttpServerUsingTest<SERVER> {

  @RegisterExtension
  private static final InstrumentationExtension testing =
      HttpServerInstrumentationExtension.forAgent();

  @BeforeAll
  void setup() {
    startServer();
  }

  @AfterAll
  void cleanup() {
    cleanupServer();
  }

  public abstract void addServlet(CONTEXT context, String path, Class<? extends Servlet> servlet);

  protected void setupServlets(CONTEXT context) {
    addServlet(context, "/prefix/*", TestServlet.class);
    addServlet(context, "*.suffix", TestServlet.class);
  }

  @ParameterizedTest
  @CsvSource({
    "prefix, /prefix/*, true",
    "prefix/, /prefix/*, true",
    "prefix/a, /prefix/*, true",
    "prefixa, /*, false",
    "a.suffix, /*.suffix, true",
    ".suffix, /*.suffix, true",
    "suffix, /*, false",
  })
  void test_path__path(String path, String route, boolean success) {

    AggregatedHttpResponse response =
        client.get(address.resolve(path).toString()).aggregate().join();

    assertThat(response.status().code()).isEqualTo(success ? 200 : 404);

    if (success) {
      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span ->
                      span.hasName("GET " + getContextPath() + route)
                          .hasKind(SpanKind.SERVER)
                          .hasStatus(StatusData.unset())));
    } else {
      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span ->
                      span.hasName("GET " + getContextPath() + route)
                          .hasKind(SpanKind.SERVER)
                          .hasStatus(
                              response.status().code() >= 500
                                  ? StatusData.error()
                                  : StatusData.unset()),
                  span -> {}));
    }
  }

  public static class TestServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
        throws IOException {
      response.getWriter().write("Ok");
    }
  }
}
