/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.reactive.server;

import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.SUCCESS;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.reactivex.Single;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.VertxOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.jdbcclient.JDBCConnectOptions;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.core.http.HttpServerResponse;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.jdbcclient.JDBCPool;
import io.vertx.reactivex.sqlclient.SqlConnection;
import io.vertx.sqlclient.PoolOptions;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VertxReactiveWebServer extends AbstractVerticle {

  private static final Logger logger = LoggerFactory.getLogger(VertxReactiveWebServer.class);

  private static final Tracer tracer = GlobalOpenTelemetry.getTracer("test");

  public static final String TEST_REQUEST_ID_PARAMETER = "test-request-id";
  public static final String TEST_REQUEST_ID_ATTRIBUTE = "test.request.id";

  private static final String CONFIG_HTTP_SERVER_PORT = "http.server.port";
  private static io.vertx.reactivex.sqlclient.Pool client;

  public static Vertx start(int port)
      throws ExecutionException, InterruptedException, TimeoutException {
    /* This is highly against Vertx ideas, but our tests are synchronous
    so we have to make sure server is up and running */
    CompletableFuture<Void> future = new CompletableFuture<>();

    Vertx server = Vertx.newInstance(io.vertx.core.Vertx.vertx(new VertxOptions()));
    client =
        JDBCPool.pool(
            server,
            new JDBCConnectOptions().setJdbcUrl("jdbc:hsqldb:mem:test?shutdown=true"),
            new PoolOptions());

    logger.info("Starting on port {}", port);
    server
        .deployVerticle(
            VertxReactiveWebServer.class.getName(),
            new DeploymentOptions().setConfig(new JsonObject().put(CONFIG_HTTP_SERVER_PORT, port)))
        .onComplete(
            res -> {
              if (!res.succeeded()) {
                RuntimeException exception =
                    new RuntimeException("Cannot deploy server Verticle", res.cause());
                future.completeExceptionally(exception);
              }
              future.complete(null);
            });

    // block until vertx server is up
    future.get(30, TimeUnit.SECONDS);

    return server;
  }

  @Override
  public void start(Promise<Void> startPromise) {
    setUpInitialData(
        ready -> {
          Router router = Router.router(vertx);
          int port = config().getInteger(CONFIG_HTTP_SERVER_PORT);
          logger.info("Listening on port {}", port);
          router
              .route(SUCCESS.getPath())
              .handler(
                  ctx -> ctx.response().setStatusCode(SUCCESS.getStatus()).end(SUCCESS.getBody()));

          router.route("/listProducts").handler(VertxReactiveWebServer::handleListProducts);

          vertx
              .createHttpServer()
              .requestHandler(router)
              .listen(port)
              .onComplete(it -> startPromise.complete());
        });
  }

  @SuppressWarnings("CheckReturnValue")
  private static void handleListProducts(RoutingContext routingContext) {
    Long requestId = extractRequestId(routingContext);
    attachRequestIdToCurrentSpan(requestId);

    Span span = tracer.spanBuilder("handleListProducts").startSpan();
    try (Scope ignored = Context.current().with(span).makeCurrent()) {
      attachRequestIdToCurrentSpan(requestId);

      HttpServerResponse response = routingContext.response();
      Single<JsonArray> jsonArraySingle = listProducts(requestId);

      jsonArraySingle.subscribe(
          arr -> response.putHeader("content-type", "application/json").end(arr.encode()));
    } finally {
      span.end();
    }
  }

  private static Single<JsonArray> listProducts(Long requestId) {
    Span span = tracer.spanBuilder("listProducts").startSpan();
    try (Scope ignored = Context.current().with(span).makeCurrent()) {
      attachRequestIdToCurrentSpan(requestId);
      String queryInfix = requestId != null ? " AS request" + requestId : "";

      return client
          .query("SELECT id" + queryInfix + ", name, price, weight FROM products")
          .rxExecute()
          .flatMap(
              result -> {
                JsonArray arr = new JsonArray();
                result.forEach(
                    row -> {
                      JsonArray values = new JsonArray();
                      for (int i = 0; i < 4; i++) {
                        values.add(row.getValue(i));
                      }
                      arr.add(values);
                    });
                return Single.just(arr);
              });
    } finally {
      span.end();
    }
  }

  private static Long extractRequestId(RoutingContext routingContext) {
    String requestIdString = routingContext.request().params().get(TEST_REQUEST_ID_PARAMETER);
    return requestIdString != null ? Long.valueOf(requestIdString) : null;
  }

  private static void attachRequestIdToCurrentSpan(Long requestId) {
    if (requestId != null) {
      Span.current().setAttribute(TEST_REQUEST_ID_ATTRIBUTE, requestId);
    }
  }

  private static void setUpInitialData(Handler<Void> done) {
    client
        .getConnection()
        .onComplete(
            res -> {
              if (res.failed()) {
                throw new IllegalStateException(res.cause());
              }

              SqlConnection conn = res.result();

              conn.query(
                      "CREATE TABLE IF NOT EXISTS products(id INT IDENTITY, name VARCHAR(255), price FLOAT, weight INT)")
                  .execute()
                  .onComplete(
                      ddl -> {
                        if (ddl.failed()) {
                          throw new IllegalStateException(ddl.cause());
                        }

                        conn.query(
                                "INSERT INTO products (name, price, weight) VALUES ('Egg Whisk', 3.99, 150), ('Tea Cosy', 5.99, 100), ('Spatula', 1.00, 80)")
                            .execute()
                            .onComplete(
                                fixtures -> {
                                  if (fixtures.failed()) {
                                    throw new IllegalStateException(fixtures.cause());
                                  }

                                  done.handle(null);
                                });
                      });
            });
  }
}
