/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.javahttpserver;

import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.CAPTURE_HEADERS;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.ERROR;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.EXCEPTION;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.INDEXED_CHILD;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.NOT_FOUND;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.QUERY_PARAM;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.REDIRECT;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.SUCCESS;

import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions;
import io.opentelemetry.testing.internal.armeria.common.QueryParams;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

public abstract class AbstractJavaHttpServerTest extends AbstractHttpServerTest<HttpServer> {

  protected void configureContexts(List<HttpContext> contexts) {}

  static void sendResponse(HttpExchange exchange, int status, String response) throws IOException {
    sendResponse(exchange, status, Collections.emptyMap(), response);
  }

  static void sendResponse(HttpExchange exchange, int status, Map<String, String> headers)
      throws IOException {
    sendResponse(exchange, status, headers, "");
  }

  static void sendResponse(
      HttpExchange exchange, int status, Map<String, String> headers, String response)
      throws IOException {

    byte[] bytes = response.getBytes(StandardCharsets.UTF_8);

    // -1 means no content, 0 means unknown content length
    long contentLength = bytes.length == 0 ? -1 : bytes.length;
    exchange.getResponseHeaders().set("Content-Type", "text/plain");
    headers.forEach(exchange.getResponseHeaders()::set);
    exchange.sendResponseHeaders(status, contentLength);
    if (bytes.length != 0) {
      try (OutputStream os = exchange.getResponseBody()) {
        os.write(bytes);
      }
    } else {
      exchange.getResponseBody().close();
    }
  }

  private static String getUrlQuery(HttpExchange exchange) {
    return exchange.getRequestURI().getQuery();
  }

  @Override
  protected HttpServer setupServer() throws IOException {
    List<HttpContext> contexts = new ArrayList<>();
    HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

    server.setExecutor(Executors.newCachedThreadPool());
    HttpContext context =
        server.createContext(
            SUCCESS.getPath(),
            ctx ->
                testing()
                    .runWithSpan(
                        "controller",
                        () -> sendResponse(ctx, SUCCESS.getStatus(), SUCCESS.getBody())));

    contexts.add(context);
    context =
        server.createContext(
            REDIRECT.getPath(),
            ctx ->
                testing()
                    .runWithSpan(
                        "controller",
                        () ->
                            sendResponse(
                                ctx,
                                REDIRECT.getStatus(),
                                Collections.singletonMap("Location", REDIRECT.getBody()))));

    contexts.add(context);
    context =
        server.createContext(
            ERROR.getPath(),
            ctx ->
                testing()
                    .runWithSpan(
                        "controller", () -> sendResponse(ctx, ERROR.getStatus(), ERROR.getBody())));

    contexts.add(context);
    context =
        server.createContext(
            QUERY_PARAM.getPath(),
            ctx ->
                testing()
                    .runWithSpan(
                        "controller",
                        () ->
                            sendResponse(
                                ctx,
                                QUERY_PARAM.getStatus(),
                                "some="
                                    + QueryParams.fromQueryString(getUrlQuery(ctx)).get("some"))));
    contexts.add(context);
    context =
        server.createContext(
            INDEXED_CHILD.getPath(),
            ctx ->
                testing()
                    .runWithSpan(
                        "controller",
                        () -> {
                          INDEXED_CHILD.collectSpanAttributes(
                              name -> QueryParams.fromQueryString(getUrlQuery(ctx)).get(name));

                          sendResponse(ctx, INDEXED_CHILD.getStatus(), INDEXED_CHILD.getBody());
                        }));
    contexts.add(context);
    context =
        server.createContext(
            "/captureHeaders",
            ctx ->
                testing()
                    .runWithSpan(
                        "controller",
                        () ->
                            sendResponse(
                                ctx,
                                CAPTURE_HEADERS.getStatus(),
                                Collections.singletonMap(
                                    "X-Test-Response",
                                    ctx.getRequestHeaders().getFirst("X-Test-Request")),
                                CAPTURE_HEADERS.getBody())));
    contexts.add(context);
    context =
        server.createContext(
            EXCEPTION.getPath(),
            ctx ->
                testing()
                    .runWithSpan(
                        "controller",
                        () -> {
                          sendResponse(ctx, EXCEPTION.getStatus(), EXCEPTION.getBody());
                          throw new IllegalStateException(EXCEPTION.getBody());
                        }));
    contexts.add(context);
    context =
        server.createContext(
            "/", ctx -> sendResponse(ctx, NOT_FOUND.getStatus(), NOT_FOUND.getBody()));
    contexts.add(context);

    configureContexts(contexts);
    server.start();

    return server;
  }

  @Override
  protected void stopServer(HttpServer server) {
    server.stop(0);
  }

  @Override
  protected void configure(HttpServerTestOptions options) {
    // filter isn't called for non-standard method
    options.disableTestNonStandardHttpMethod();
    options.setTestHttpPipelining(
        Double.parseDouble(System.getProperty("java.specification.version")) >= 21);
    options.setExpectedHttpRoute(
        (endpoint, method) -> {
          if (NOT_FOUND.equals(endpoint)) {
            return "/";
          }
          return expectedHttpRoute(endpoint, method);
        });
  }
}
