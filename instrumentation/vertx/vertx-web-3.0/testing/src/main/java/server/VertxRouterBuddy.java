/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package server;

import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.CAPTURE_HEADERS;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.ERROR;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.EXCEPTION;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.INDEXED_CHILD;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.PATH_PARAM;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.QUERY_PARAM;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.REDIRECT;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.SUCCESS;

import io.opentelemetry.instrumentation.test.base.HttpServerTest;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public final class VertxRouterBuddy {
  public static final String CONFIG_HTTP_SERVER_PORT = "http.server.port";

  private VertxRouterBuddy() {}

  public static Router buildRouter(Vertx vertx) {
    Router router = Router.router(vertx);

    //noinspection Convert2Lambda
    router
        .route(SUCCESS.getPath())
        .handler(
            // This is not a closure/lambda on purpose to verify how do we instrument actual Handler
            // classes
            new Handler<RoutingContext>() {
              @Override
              public void handle(RoutingContext ctx) {
                HttpServerTest.controller(
                    SUCCESS,
                    () -> {
                      ctx.response().setStatusCode(SUCCESS.getStatus()).end(SUCCESS.getBody());
                      return null;
                    });
              }
            });
    router
        .route(INDEXED_CHILD.getPath())
        .handler(
            ctx ->
                HttpServerTest.controller(
                    INDEXED_CHILD,
                    () -> {
                      INDEXED_CHILD.collectSpanAttributes(it -> ctx.request().getParam(it));
                      ctx.response().setStatusCode(INDEXED_CHILD.getStatus()).end();
                      return null;
                    }));
    router
        .route(QUERY_PARAM.getPath())
        .handler(
            ctx ->
                HttpServerTest.controller(
                    QUERY_PARAM,
                    () -> {
                      ctx.response()
                          .setStatusCode(QUERY_PARAM.getStatus())
                          .end(ctx.request().query());
                      return null;
                    }));
    router
        .route(REDIRECT.getPath())
        .handler(
            ctx ->
                HttpServerTest.controller(
                    REDIRECT,
                    () -> {
                      ctx.response()
                          .setStatusCode(REDIRECT.getStatus())
                          .putHeader("location", REDIRECT.getBody())
                          .end();
                      return null;
                    }));
    router
        .route(ERROR.getPath())
        .handler(
            ctx ->
                HttpServerTest.controller(
                    ERROR,
                    () -> {
                      ctx.response().setStatusCode(ERROR.getStatus()).end(ERROR.getBody());
                      return null;
                    }));
    router
        .route(EXCEPTION.getPath())
        .handler(
            ctx ->
                HttpServerTest.controller(
                    EXCEPTION,
                    () -> {
                      throw new Exception(EXCEPTION.getBody());
                    }));
    router
        .route("/path/:id/param")
        .handler(
            ctx ->
                HttpServerTest.controller(
                    PATH_PARAM,
                    () -> {
                      ctx.response()
                          .setStatusCode(PATH_PARAM.getStatus())
                          .end(ctx.request().getParam("id"));
                      return null;
                    }));
    router
        .route(CAPTURE_HEADERS.getPath())
        .handler(
            ctx ->
                HttpServerTest.controller(
                    CAPTURE_HEADERS,
                    () -> {
                      ctx.response()
                          .setStatusCode(CAPTURE_HEADERS.getStatus())
                          .putHeader("X-Test-Response", ctx.request().getHeader("X-Test-Request"))
                          .end(CAPTURE_HEADERS.getBody());
                      return null;
                    }));

    return router;
  }
}
