package io.opentelemetry.instrumentation.httpserver;

import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.CAPTURE_HEADERS;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.ERROR;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.EXCEPTION;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.INDEXED_CHILD;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.PATH_PARAM;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.QUERY_PARAM;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.REDIRECT;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.SUCCESS;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions;
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint;
import io.opentelemetry.testing.internal.armeria.common.QueryParams;

public abstract class AbstractJdkHttpServerTest extends AbstractHttpServerTest<HttpServer> {

  List<HttpContext> contexts = new ArrayList<>();

  protected Filter customFilter() {
    return null;
  }

  void sendResponse(HttpExchange exchange, int status, String response) throws IOException {
    sendResponse(exchange, status, Collections.emptyMap(), response);
  }

  void sendResponse(HttpExchange exchange, int status, Map<String, String> headers)
      throws IOException {
    sendResponse(exchange, status, headers, "");
  }

  void sendResponse(HttpExchange exchange, int status, Map<String, String> headers, String response)
      throws IOException {

    byte[] bytes = response.getBytes(Charset.defaultCharset());

    // -1 means no content, 0 means unknown content length
    long contentLength = bytes.length == 0 ? -1 : bytes.length;
    exchange.getResponseHeaders().set("Content-Type", "text/plain");
    headers.forEach(exchange.getResponseHeaders()::set);
    try (OutputStream os = exchange.getResponseBody()) {
      exchange.sendResponseHeaders(status, contentLength);
      os.write(bytes);
    }
  }

  public String getUrlQuery(HttpExchange exchange) {
    String fullPath = exchange.getRequestURI().toString();
    int separatorPos = fullPath.indexOf('?');
    return separatorPos == -1 ? null : fullPath.substring(separatorPos + 1);
  }

  @Override
  protected HttpServer setupServer() throws IOException {
    HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

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
            EXCEPTION.getPath(),
            ctx ->
                testing()
                    .runWithSpan(
                        "controller",
                        () -> {
                          throw new IllegalStateException(EXCEPTION.getBody());
                        }));
    contexts.add(context);
    context =
        server.createContext(
            "/query",
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
            "/path/:id/param",
            ctx ->
                testing()
                    .runWithSpan(
                        "controller", () -> sendResponse(ctx, PATH_PARAM.getStatus(), "id")));
    contexts.add(context);
    context =
        server.createContext(
            "/child",
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

    Filter customFilter = customFilter();
    if (customFilter != null) {
      contexts.forEach(ctx -> ctx.getFilters().add(customFilter));
    }

    // Make sure user decorators see spans.
    Filter spanFilter =
        new Filter() {

          @Override
          public void doFilter(HttpExchange exchange, Chain chain) throws IOException {

            if (!Span.current().getSpanContext().isValid()) {
              // Return an invalid code to fail any assertion

              exchange.sendResponseHeaders(601, -1);
            }
            exchange.getResponseHeaders().set("decoratingfunction", "ok");
            exchange.getResponseHeaders().set("decoratinghttpservicefunction", "ok");
          }

          @Override
          public String description() {
            return "test";
          }
        };
    contexts.forEach(ctx -> ctx.getFilters().add(spanFilter));
    server.start();

    return server;
  }

  @Override
  protected void stopServer(HttpServer server) {
    server.stop(1000);
  }

  @Override
  protected void configure(HttpServerTestOptions options) {
    options.setExpectedHttpRoute(
        (endpoint, method) -> {
          if (endpoint == ServerEndpoint.NOT_FOUND) {
            // TODO: Revisit this when applying instrumenters to more libraries, Armeria currently
            // reports '/*' which is a fallback route.
            return "/*";
          }
          return expectedHttpRoute(endpoint, method);
        });

    options.setTestNotFound(false);
    options.setTestPathParam(false);
    options.setTestException(false);
  }
}
