/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.helidon;

import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.CAPTURE_HEADERS;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.ERROR;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.EXCEPTION;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.INDEXED_CHILD;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.NOT_FOUND;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.QUERY_PARAM;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.REDIRECT;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.SUCCESS;

import io.helidon.http.HeaderNames;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions;
import io.opentelemetry.testing.internal.armeria.common.QueryParams;
import java.util.Collections;
import java.util.Map;

public abstract class AbstractHelidonTest extends AbstractHttpServerTest<WebServer> {

  protected void configureRoutes(HttpRouting.Builder routing) {}

  static void sendResponse(ServerResponse res, int status, String response) {
    sendResponse(res, status, Collections.emptyMap(), response);
  }

  static void sendResponse(ServerResponse res, int status, Map<String, String> headers) {
    sendResponse(res, status, headers, "");
  }

  static void sendResponse(
      ServerResponse res, int status, Map<String, String> headers, String response) {
    res.header("Content-Type", "text/plain");
    headers.forEach(res::header);
    res.status(status).send(response);
  }

  private static String getUrlQuery(ServerRequest req) {
    return req.query().rawValue();
  }

  @Override
  protected WebServer setupServer() {
    var server = WebServer.builder().port(port);
    var routing = HttpRouting.builder();

    routing.get(
        SUCCESS.getPath(),
        (req, res) ->
            testing()
                .runWithSpan(
                    "controller", () -> sendResponse(res, SUCCESS.getStatus(), SUCCESS.getBody())));

    routing.get(
        REDIRECT.getPath(),
        (req, res) ->
            testing()
                .runWithSpan(
                    "controller",
                    () ->
                        sendResponse(
                            res,
                            REDIRECT.getStatus(),
                            Collections.singletonMap("Location", REDIRECT.getBody()))));

    routing.get(
        ERROR.getPath(),
        (req, res) ->
            testing()
                .runWithSpan(
                    "controller", () -> sendResponse(res, ERROR.getStatus(), ERROR.getBody())));

    routing.get(
        QUERY_PARAM.getPath(),
        (req, res) ->
            testing()
                .runWithSpan(
                    "controller",
                    () ->
                        sendResponse(
                            res,
                            QUERY_PARAM.getStatus(),
                            "some=" + QueryParams.fromQueryString(getUrlQuery(req)).get("some"))));

    routing.get(
        INDEXED_CHILD.getPath(),
        (req, res) ->
            testing()
                .runWithSpan(
                    "controller",
                    () -> {
                      INDEXED_CHILD.collectSpanAttributes(
                          name -> QueryParams.fromQueryString(getUrlQuery(req)).get(name));

                      sendResponse(res, INDEXED_CHILD.getStatus(), INDEXED_CHILD.getBody());
                    }));

    routing.get(
        "/captureHeaders",
        (req, res) ->
            testing()
                .runWithSpan(
                    "controller",
                    () ->
                        sendResponse(
                            res,
                            CAPTURE_HEADERS.getStatus(),
                            Collections.singletonMap(
                                "X-Test-Response",
                                req.headers().get(HeaderNames.create("X-Test-Request")).get()),
                            CAPTURE_HEADERS.getBody())));

    routing.get(
        EXCEPTION.getPath(),
        (req, res) ->
            testing()
                .runWithSpan(
                    "controller",
                    () -> {
                      sendResponse(res, EXCEPTION.getStatus(), EXCEPTION.getBody());
                      throw new IllegalStateException(EXCEPTION.getBody());
                    }));

    routing.get("/", (req, res) -> sendResponse(res, NOT_FOUND.getStatus(), NOT_FOUND.getBody()));
    configureRoutes(routing);

    return server.routing(routing).build().start();
  }

  @Override
  protected void stopServer(WebServer server) {
    server.stop();
  }

  @Override
  protected void configure(HttpServerTestOptions options) {
    // filter isn't called for non-standard method
    options.disableTestNonStandardHttpMethod();
    options.setTestException(false);
    options.setTestHttpPipelining(true);
  }
}
