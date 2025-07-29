/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v5_0;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerUsingTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpResponse;
import jakarta.servlet.Servlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public abstract class AbstractServlet5MappingTest<SERVER, CONTEXT>
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

  public abstract void addServlet(CONTEXT context, String path, Class<? extends Servlet> servlet)
      throws Exception;

  protected void setupServlets(CONTEXT context) throws Exception {
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
  void testPath(String path, String route, boolean success) {

    AggregatedHttpResponse response =
        client.get(address.resolve(path).toString()).aggregate().join();

    assertThat(response.status().code()).isEqualTo(success ? 200 : 404);

    testing.waitAndAssertTraces(
        trace -> {
          List<Consumer<SpanDataAssert>> assertions = new ArrayList<>();
          assertions.add(
              span ->
                  span.hasName("GET " + getContextPath() + route)
                      .hasKind(SpanKind.SERVER)
                      .hasStatus(
                          !success && response.status().code() >= 500
                              ? StatusData.error()
                              : StatusData.unset()));
          if (!success) {
            assertions.add(span -> {});
          }

          trace.hasSpansSatisfyingExactly(assertions);
        });
  }

  public static class TestServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
        throws IOException {
      response.getWriter().write("Ok");
    }
  }
}
