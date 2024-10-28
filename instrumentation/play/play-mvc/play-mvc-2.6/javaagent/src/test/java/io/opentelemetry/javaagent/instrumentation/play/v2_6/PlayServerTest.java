/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.play.v2_6;

import static io.opentelemetry.api.trace.SpanKind.INTERNAL;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.CAPTURE_HEADERS;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.ERROR;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.EXCEPTION;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.INDEXED_CHILD;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.QUERY_PARAM;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.REDIRECT;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.SUCCESS;
import static java.util.Collections.emptySet;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions;
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.trace.data.StatusData;
import org.junit.jupiter.api.extension.RegisterExtension;
import play.Mode;
import play.mvc.Controller;
import play.mvc.Results;
import play.routing.RoutingDsl;
import play.server.Server;

class PlayServerTest extends AbstractHttpServerTest<Server> {

  @RegisterExtension
  static final InstrumentationExtension testing = HttpServerInstrumentationExtension.forAgent();

  @Override
  protected Server setupServer() {
    return Server.forRouter(
        Mode.TEST,
        port,
        components ->
            RoutingDsl.fromComponents(components)
                .GET(SUCCESS.getPath())
                .routeTo(
                    () ->
                        controller(
                            SUCCESS, () -> Results.status(SUCCESS.getStatus(), SUCCESS.getBody())))
                .GET(QUERY_PARAM.getPath())
                .routeTo(
                    () ->
                        controller(
                            QUERY_PARAM,
                            () -> Results.status(QUERY_PARAM.getStatus(), QUERY_PARAM.getBody())))
                .GET(REDIRECT.getPath())
                .routeTo(() -> controller(REDIRECT, () -> Results.found(REDIRECT.getBody())))
                .GET(ERROR.getPath())
                .routeTo(
                    () ->
                        controller(ERROR, () -> Results.status(ERROR.getStatus(), ERROR.getBody())))
                .GET(EXCEPTION.getPath())
                .routeTo(
                    () ->
                        controller(
                            EXCEPTION,
                            () -> {
                              throw new IllegalArgumentException(EXCEPTION.getBody());
                            }))
                .GET(CAPTURE_HEADERS.getPath())
                .routeTo(
                    () ->
                        controller(
                            CAPTURE_HEADERS,
                            () -> {
                              Controller.request()
                                  .header("X-Test-Request")
                                  .ifPresent(
                                      value ->
                                          Controller.response()
                                              .setHeader("X-Test-Response", value));
                              return Results.status(
                                  CAPTURE_HEADERS.getStatus(), CAPTURE_HEADERS.getBody());
                            }))
                .GET(INDEXED_CHILD.getPath())
                .routeTo(
                    () ->
                        controller(
                            INDEXED_CHILD,
                            () -> {
                              INDEXED_CHILD.collectSpanAttributes(
                                  name -> Controller.request().getQueryString(name));
                              return Results.status(
                                  INDEXED_CHILD.getStatus(), INDEXED_CHILD.getBody());
                            }))
                .build());
  }

  @Override
  protected void stopServer(Server server) throws Exception {
    server.stop();
  }

  @Override
  protected void configure(HttpServerTestOptions options) {
    super.configure(options);
    options.setHasHandlerSpan(unused -> true);
    options.setHttpAttributes(endpoint -> emptySet());
    options.setExpectedException(new IllegalArgumentException(EXCEPTION.getBody()));
    options.disableTestNonStandardHttpMethod();
  }

  @Override
  protected SpanDataAssert assertHandlerSpan(
      SpanDataAssert span, String method, ServerEndpoint endpoint) {
    span.hasName("play.request").hasKind(INTERNAL);
    if (endpoint == EXCEPTION) {
      span.hasStatus(StatusData.error());
      span.hasException(new IllegalArgumentException(EXCEPTION.getBody()));
    }
    return span;
  }
}
