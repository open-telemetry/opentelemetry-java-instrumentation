/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.undertow;

import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.CAPTURE_HEADERS;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.ERROR;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.INDEXED_CHILD;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.QUERY_PARAM;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.REDIRECT;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.SUCCESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;

import com.google.common.collect.ImmutableSet;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions;
import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.StatusCodes;
import org.junit.jupiter.api.extension.RegisterExtension;

class UndertowServerDispatchTest extends AbstractHttpServerTest<Undertow> {

  @RegisterExtension
  static final InstrumentationExtension testing = HttpServerInstrumentationExtension.forAgent();

  @Override
  public Undertow setupServer() {
    Undertow server =
        Undertow.builder()
            .addHttpListener(port, "localhost")
            .setHandler(
                Handlers.path()
                    .addExactPath(
                        SUCCESS.rawPath(),
                        exchange ->
                            exchange.dispatch(
                                k ->
                                    controller(
                                        SUCCESS,
                                        () -> k.getResponseSender().send(SUCCESS.getBody()))))
                    .addExactPath(
                        QUERY_PARAM.rawPath(),
                        exchange ->
                            exchange.dispatch(
                                k ->
                                    controller(
                                        QUERY_PARAM,
                                        () -> k.getResponseSender().send(k.getQueryString()))))
                    .addExactPath(
                        REDIRECT.rawPath(),
                        exchange ->
                            exchange.dispatch(
                                k ->
                                    controller(
                                        REDIRECT,
                                        () -> {
                                          k.setStatusCode(StatusCodes.FOUND);
                                          k.getResponseHeaders()
                                              .put(Headers.LOCATION, REDIRECT.getBody());
                                          k.endExchange();
                                        })))
                    .addExactPath(
                        CAPTURE_HEADERS.rawPath(),
                        exchange ->
                            exchange.dispatch(
                                k ->
                                    controller(
                                        CAPTURE_HEADERS,
                                        () -> {
                                          k.setStatusCode(StatusCodes.OK);
                                          k.getResponseHeaders()
                                              .put(
                                                  new HttpString("X-Test-Response"),
                                                  exchange
                                                      .getRequestHeaders()
                                                      .getFirst("X-Test-Request"));
                                          k.getResponseSender().send(CAPTURE_HEADERS.getBody());
                                        })))
                    .addExactPath(
                        ERROR.rawPath(),
                        exchange ->
                            exchange.dispatch(
                                k ->
                                    controller(
                                        ERROR,
                                        () -> {
                                          exchange.setStatusCode(ERROR.getStatus());
                                          exchange.getResponseSender().send(ERROR.getBody());
                                        })))
                    .addExactPath(
                        INDEXED_CHILD.rawPath(),
                        exchange ->
                            exchange.dispatch(
                                k ->
                                    controller(
                                        INDEXED_CHILD,
                                        () -> {
                                          INDEXED_CHILD.collectSpanAttributes(
                                              name ->
                                                  exchange
                                                      .getQueryParameters()
                                                      .get(name)
                                                      .peekFirst());
                                          exchange
                                              .getResponseSender()
                                              .send(INDEXED_CHILD.getBody());
                                        }))))
            .build();
    server.start();
    return server;
  }

  @Override
  public void stopServer(Undertow undertow) {
    undertow.stop();
  }

  @Override
  protected void configure(HttpServerTestOptions options) {
    super.configure(options);
    options.setVerifyServerSpanEndTime(false);
    // throwing exception from dispatched task just makes the request time out
    options.setTestException(false);
    options.setHasResponseCustomizer(endpoint -> true);

    options.setHttpAttributes(endpoint -> ImmutableSet.of(NETWORK_PEER_PORT));
  }
}
