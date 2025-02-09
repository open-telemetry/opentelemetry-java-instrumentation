/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.struts.v2_3;

import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.ERROR;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.EXCEPTION;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.NOT_FOUND;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.PATH_PARAM;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.REDIRECT;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.incubating.CodeIncubatingAttributes.CODE_FUNCTION;
import static io.opentelemetry.semconv.incubating.CodeIncubatingAttributes.CODE_NAMESPACE;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.api.internal.HttpConstants;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions;
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpResponse;
import java.util.EnumSet;
import java.util.Locale;
import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.session.HashSessionIdManager;
import org.eclipse.jetty.server.session.HashSessionManager;
import org.eclipse.jetty.server.session.SessionHandler;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.util.resource.FileResource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class Struts2ActionSpanTest extends AbstractHttpServerTest<Server> {

  @RegisterExtension
  public static final InstrumentationExtension testing =
      HttpServerInstrumentationExtension.forAgent();

  @Override
  @SuppressWarnings("unchecked")
  protected Server setupServer() throws Exception {
    Server server = new Server(port);
    ServletContextHandler context = new ServletContextHandler(0);
    context.setContextPath(getContextPath());
    FileResource resource = new FileResource(getClass().getResource("/"));
    context.setBaseResource(resource);
    server.setHandler(context);

    HashSessionIdManager sessionIdManager = new HashSessionIdManager();
    server.setSessionIdManager(sessionIdManager);
    HashSessionManager sessionManager = new HashSessionManager();
    SessionHandler sessionHandler = new SessionHandler(sessionManager);
    context.setHandler(sessionHandler);

    // disable adding jsessionid to url, affects redirect test
    context.setInitParameter("org.eclipse.jetty.servlet.SessionIdPathParameterName", "none");

    context.addServlet(DefaultServlet.class, "/");
    context.addServlet(GreetingServlet.class, "/greetingServlet");
    Class<?> strutsFilterClass;
    try {
      // struts 2.3
      strutsFilterClass =
          Class.forName("org.apache.struts2.dispatcher.ng.filter.StrutsPrepareAndExecuteFilter");
    } catch (ClassNotFoundException exception) {
      // struts 2.5
      strutsFilterClass =
          Class.forName("org.apache.struts2.dispatcher.filter.StrutsPrepareAndExecuteFilter");
    }
    context.addFilter(
        (Class<? extends Filter>) strutsFilterClass, "/*", EnumSet.of(DispatcherType.REQUEST));

    server.start();
    return server;
  }

  @Override
  protected void stopServer(Server server) throws Exception {
    server.stop();
    server.destroy();
  }

  @Override
  protected void configure(HttpServerTestOptions options) {
    options.setContextPath("/context");
    options.setTestPathParam(true);
    options.setTestErrorBody(false);
    options.setHasHandlerSpan(endpoint -> !endpoint.equals(NOT_FOUND));
    options.setHasResponseSpan(
        endpoint ->
            endpoint == REDIRECT
                || endpoint == ERROR
                || endpoint == EXCEPTION
                || endpoint == NOT_FOUND);

    options.setExpectedHttpRoute(
        (ServerEndpoint endpoint, String method) -> {
          if (method.equals(HttpConstants._OTHER)) {
            return getContextPath() + endpoint.getPath();
          }
          if (endpoint.equals(PATH_PARAM)) {
            return getContextPath() + "/path/{id}/param";
          } else if (endpoint.equals(NOT_FOUND)) {
            return getContextPath() + "/*";
          } else {
            return super.expectedHttpRoute(endpoint, method);
          }
        });
  }

  @Override
  protected SpanDataAssert assertResponseSpan(
      SpanDataAssert span, SpanData parentSpan, String method, ServerEndpoint endpoint) {
    if (endpoint.equals(REDIRECT)) {
      span.satisfies(spanData -> assertThat(spanData.getName()).endsWith(".sendRedirect"));
    } else if (endpoint.equals(NOT_FOUND)) {
      span.satisfies(spanData -> assertThat(spanData.getName()).endsWith(".sendError"))
          .hasParent(parentSpan);
    }

    span.hasKind(SpanKind.INTERNAL);
    return span;
  }

  @SuppressWarnings("deprecation") // using deprecated semconv
  @Override
  protected SpanDataAssert assertHandlerSpan(
      SpanDataAssert span, String method, ServerEndpoint endpoint) {
    span.hasName("GreetingAction." + endpoint.name().toLowerCase(Locale.ROOT))
        .hasKind(SpanKind.INTERNAL);

    if (endpoint.equals(EXCEPTION)) {
      span.hasStatus(StatusData.error())
          .hasException(new IllegalStateException(EXCEPTION.getBody()));
    }

    span.hasAttributesSatisfyingExactly(
        equalTo(CODE_NAMESPACE, GreetingAction.class.getName()),
        equalTo(CODE_FUNCTION, endpoint.name().toLowerCase(Locale.ROOT)));
    return span;
  }

  // Struts runs from a servlet filter. Test that dispatching from struts action to a servlet
  // does not overwrite server span name given by struts instrumentation.
  @Test
  void testDispatchToServlet() {
    AggregatedHttpResponse response =
        client.get(address.resolve("dispatch").toString()).aggregate().join();

    assertThat(response.status().code()).isEqualTo(200);
    assertThat(response.contentUtf8()).isEqualTo("greeting");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("GET " + getContextPath() + "/dispatch")
                        .hasKind(SpanKind.SERVER)
                        .hasNoParent(),
                span ->
                    span.hasName("GreetingAction.dispatch_servlet")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0))));
  }
}
