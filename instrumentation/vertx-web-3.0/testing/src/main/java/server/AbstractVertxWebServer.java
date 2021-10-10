/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package server;

import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.ERROR;
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.EXCEPTION;
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.INDEXED_CHILD;
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.PATH_PARAM;
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.QUERY_PARAM;
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.REDIRECT;
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.SUCCESS;

import io.opentelemetry.instrumentation.test.base.HttpServerTest;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public abstract class AbstractVertxWebServer extends AbstractVerticle {
  public static final String CONFIG_HTTP_SERVER_PORT = "http.server.port";

  public abstract void end(HttpServerResponse response, String message);

  public abstract void end(HttpServerResponse response);

  public Router buildRouter() {
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
                HttpServerTest.controller(SUCCESS, () -> null);
                end(ctx.response().setStatusCode(SUCCESS.getStatus()), SUCCESS.getBody());
              }
            });
    router
        .route(INDEXED_CHILD.getPath())
        .handler(
            ctx -> {
              HttpServerTest.controller(
                  INDEXED_CHILD,
                  () -> {
                    INDEXED_CHILD.collectSpanAttributes(it -> ctx.request().getParam(it));
                    return null;
                  });
              end(ctx.response().setStatusCode(INDEXED_CHILD.getStatus()));
            });
    router
        .route(QUERY_PARAM.getPath())
        .handler(
            ctx -> {
              HttpServerTest.controller(QUERY_PARAM, () -> null);
              end(ctx.response().setStatusCode(QUERY_PARAM.getStatus()), ctx.request().query());
            });
    router
        .route(REDIRECT.getPath())
        .handler(
            ctx -> {
              HttpServerTest.controller(REDIRECT, () -> null);
              end(
                  ctx.response()
                      .setStatusCode(REDIRECT.getStatus())
                      .putHeader("location", REDIRECT.getBody()));
            });
    router
        .route(ERROR.getPath())
        .handler(
            ctx -> {
              HttpServerTest.controller(ERROR, () -> null);
              end(ctx.response().setStatusCode(ERROR.getStatus()), ERROR.getBody());
            });
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
            ctx -> {
              HttpServerTest.controller(PATH_PARAM, () -> null);
              end(
                  ctx.response().setStatusCode(PATH_PARAM.getStatus()),
                  ctx.request().getParam("id"));
            });

    return router;
  }
}
