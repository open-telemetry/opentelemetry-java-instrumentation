/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jetty.v12_0;

import static io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions.DEFAULT_HTTP_ATTRIBUTES;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.CAPTURE_HEADERS;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.ERROR;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.EXCEPTION;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.INDEXED_CHILD;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.NOT_FOUND;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.QUERY_PARAM;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.REDIRECT;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.SUCCESS;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_ROUTE;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Sets;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions;
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.semconv.incubating.CodeIncubatingAttributes;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.Callback;
import org.junit.jupiter.api.extension.RegisterExtension;

class Jetty12HandlerTest extends AbstractHttpServerTest<Server> {

  @RegisterExtension
  static final InstrumentationExtension testing = HttpServerInstrumentationExtension.forAgent();

  private final TestHandler testHandler = new TestHandler();

  @Override
  protected Server setupServer() throws Exception {
    Server server = new Server(port);
    server.setHandler(testHandler);
    server.start();
    return server;
  }

  @Override
  protected void stopServer(Server server) throws Exception {
    server.stop();
  }

  @Override
  protected void configure(HttpServerTestOptions options) {
    options.setHttpAttributes(
        unused -> Sets.difference(DEFAULT_HTTP_ATTRIBUTES, Collections.singleton(HTTP_ROUTE)));
    options.setHasResponseCustomizer(endpoint -> endpoint != EXCEPTION);
  }

  @SuppressWarnings("deprecation") // using deprecated semconv
  @Override
  protected SpanDataAssert assertResponseSpan(
      SpanDataAssert span, String method, ServerEndpoint endpoint) {
    String methodName;
    if (endpoint == REDIRECT) {
      methodName = "sendRedirect";
    } else if (endpoint == ERROR) {
      methodName = "sendError";
    } else {
      throw new AssertionError("Unexpected endpoint: " + endpoint.name());
    }
    span.hasKind(SpanKind.INTERNAL)
        .satisfies(spanData -> assertThat(spanData.getName()).endsWith("." + methodName))
        .hasAttributesSatisfyingExactly(
            equalTo(CodeIncubatingAttributes.CODE_FUNCTION, methodName),
            equalTo(CodeIncubatingAttributes.CODE_NAMESPACE, "org.eclipse.jetty.server.Response"));
    return span;
  }

  private void handleRequest(Request request, Response response) {
    ServerEndpoint endpoint = ServerEndpoint.forPath(request.getHttpURI().getPath());
    controller(
        endpoint,
        () -> {
          try {
            response(request, response, endpoint);
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
          return null;
        });
  }

  private void response(Request request, Response response, ServerEndpoint endpoint)
      throws IOException {
    if (SUCCESS.equals(endpoint)) {
      response.setStatus(endpoint.getStatus());
      response.write(true, StandardCharsets.UTF_8.encode(endpoint.getBody()), Callback.NOOP);
    } else if (QUERY_PARAM.equals(endpoint)) {
      response.setStatus(endpoint.getStatus());
      response.write(
          true, StandardCharsets.UTF_8.encode(request.getHttpURI().getQuery()), Callback.NOOP);
    } else if (REDIRECT.equals(endpoint)) {
      response.setStatus(endpoint.getStatus());
      response.getHeaders().add("Location", "http://localhost:" + port + endpoint.getBody());
    } else if (ERROR.equals(endpoint)) {
      response.setStatus(endpoint.getStatus());
      response.write(true, StandardCharsets.UTF_8.encode(endpoint.getBody()), Callback.NOOP);
    } else if (CAPTURE_HEADERS.equals(endpoint)) {
      response.getHeaders().add("X-Test-Response", request.getHeaders().get("X-Test-Request"));
      response.setStatus(endpoint.getStatus());
      response.write(true, StandardCharsets.UTF_8.encode(endpoint.getBody()), Callback.NOOP);
    } else if (EXCEPTION.equals(endpoint)) {
      throw new IllegalStateException(endpoint.getBody());
    } else if (INDEXED_CHILD.equals(endpoint)) {
      INDEXED_CHILD.collectSpanAttributes(
          name -> Request.extractQueryParameters(request).getValue(name));
      response.setStatus(endpoint.getStatus());
      response.write(true, StandardCharsets.UTF_8.encode(endpoint.getBody()), Callback.NOOP);
    } else {
      response.setStatus(NOT_FOUND.getStatus());
      response.write(true, StandardCharsets.UTF_8.encode(NOT_FOUND.getBody()), Callback.NOOP);
    }
  }

  private class TestHandler extends Handler.Abstract {

    @Override
    public boolean handle(Request baseRequest, Response response, Callback callback) {
      handleRequest(baseRequest, response);

      callback.succeeded();
      return true;
    }
  }
}
