/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.play.v2_4.server;

import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.CAPTURE_HEADERS;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.ERROR;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.EXCEPTION;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.INDEXED_CHILD;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.QUERY_PARAM;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.REDIRECT;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.SUCCESS;

import java.util.Collections;
import play.libs.F;
import play.mvc.Results;
import play.routing.RoutingDsl;
import play.server.Server;
import scala.Tuple2;
import scala.collection.JavaConverters;

class PlayAsyncServerTest extends PlayServerTest {

  @Override
  protected Server setupServer() {
    RoutingDsl router =
        new RoutingDsl()
            .GET(SUCCESS.getPath())
            .routeAsync(
                () ->
                    F.Promise.promise(
                        () ->
                            controller(
                                SUCCESS,
                                () -> Results.status(SUCCESS.getStatus(), SUCCESS.getBody()))))
            .GET(INDEXED_CHILD.getPath())
            .routeAsync(
                () ->
                    F.Promise.promise(
                        () ->
                            controller(
                                INDEXED_CHILD,
                                () -> {
                                  INDEXED_CHILD.collectSpanAttributes(
                                      it ->
                                          play.mvc.Http.Context.Implicit.request()
                                              .getQueryString(it));
                                  return Results.status(INDEXED_CHILD.getStatus());
                                })))
            .GET(QUERY_PARAM.getPath())
            .routeAsync(
                () ->
                    F.Promise.promise(
                        () ->
                            controller(
                                QUERY_PARAM,
                                () ->
                                    Results.status(
                                        QUERY_PARAM.getStatus(), QUERY_PARAM.getBody()))))
            .GET(REDIRECT.getPath())
            .routeAsync(
                () ->
                    F.Promise.promise(
                        () -> controller(REDIRECT, () -> Results.found(REDIRECT.getBody()))))
            .GET(CAPTURE_HEADERS.getPath())
            .routeAsync(
                () ->
                    F.Promise.promise(
                        () ->
                            controller(
                                CAPTURE_HEADERS,
                                () -> {
                                  Results.Status javaResult =
                                      Results.status(
                                          CAPTURE_HEADERS.getStatus(), CAPTURE_HEADERS.getBody());
                                  Tuple2<String, String> header =
                                      new Tuple2<>(
                                          "X-Test-Response",
                                          play.mvc.Http.Context.Implicit.request()
                                              .getHeader("X-Test-Request"));
                                  return new Results.Status(
                                      javaResult
                                          .toScala()
                                          .withHeaders(
                                              JavaConverters.asScalaIteratorConverter(
                                                      Collections.singletonList(header).iterator())
                                                  .asScala()
                                                  .toSeq()));
                                })))
            .GET(ERROR.getPath())
            .routeAsync(
                () ->
                    F.Promise.promise(
                        () ->
                            controller(
                                ERROR, () -> Results.status(ERROR.getStatus(), ERROR.getBody()))))
            .GET(EXCEPTION.getPath())
            .routeAsync(
                () ->
                    F.Promise.promise(
                        () ->
                            controller(
                                EXCEPTION,
                                () -> {
                                  throw new IllegalArgumentException(EXCEPTION.getBody());
                                })));

    return Server.forRouter(router.build(), port);
  }
}
