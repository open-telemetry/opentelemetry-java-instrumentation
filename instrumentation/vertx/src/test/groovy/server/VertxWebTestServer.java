package server;

import io.opentelemetry.auto.test.base.HttpServerTest;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.ERROR;
import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.EXCEPTION;
import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.PATH_PARAM;
import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.QUERY_PARAM;
import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.REDIRECT;
import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.SUCCESS;

public class VertxWebTestServer extends AbstractVerticle {

  @Override
  public void start(final Future<Void> startFuture) {
    final int port = config().getInteger(VertxHttpServerTest.CONFIG_HTTP_SERVER_PORT);
    final Router router = Router.router(vertx);

    //noinspection Convert2Lambda
    router.route(SUCCESS.getPath()).handler(new Handler<RoutingContext>() {
      @Override
      public void handle(RoutingContext ctx) {
        HttpServerTest.controller(SUCCESS, () -> {
          ctx.response().setStatusCode(SUCCESS.getStatus()).end(SUCCESS.getBody());
          return null;
        });
      }
    });
    router.route(QUERY_PARAM.getPath()).handler(ctx -> {
      HttpServerTest.controller(QUERY_PARAM, () -> {
        ctx.response().setStatusCode(QUERY_PARAM.getStatus()).end(ctx.request().query());
        return null;
      });
    });
    router.route(REDIRECT.getPath()).handler(ctx -> {
      HttpServerTest.controller(REDIRECT, () -> {
        ctx.response().setStatusCode(REDIRECT.getStatus())
            .putHeader("location", REDIRECT.getBody())
            .end();
        return null;
      });
    });
    router.route(ERROR.getPath()).handler(ctx -> {
      HttpServerTest.controller(ERROR, () -> {
        ctx.response().setStatusCode(ERROR.getStatus()).end(ERROR.getBody());
        return null;
      });
    });
    router.route(EXCEPTION.getPath()).handler(ctx -> {
      HttpServerTest.controller(EXCEPTION, () -> {
        throw new Exception(EXCEPTION.getBody());
      });
    });
    router.route("/path/:id/param").handler(ctx -> {
      HttpServerTest.controller(PATH_PARAM, () -> {
        ctx.response().setStatusCode(PATH_PARAM.getStatus()).end(ctx.request().getParam("id"));
        return null;
      });
    });

    vertx.createHttpServer().requestHandler(router::accept)
        .listen(port, it -> startFuture.complete());
  }
}
