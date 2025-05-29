/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.reactive.server;

import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.EXCEPTION;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions;
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint;
import io.vertx.circuitbreaker.CircuitBreakerOptions;
import io.vertx.core.Future;
import io.vertx.reactivex.circuitbreaker.CircuitBreaker;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.junit.jupiter.api.extension.RegisterExtension;

class VertxRxCircuitBreakerHttpServerTest extends AbstractVertxRxHttpServerTest {

  @RegisterExtension
  static final InstrumentationExtension testing = HttpServerInstrumentationExtension.forAgent();

  @Override
  protected void configure(HttpServerTestOptions options) {
    super.configure(options);

    options.setTestHttpPipelining(false);
    options.setHasExceptionOnServerSpan(endpoint -> endpoint != EXCEPTION);
  }

  @Override
  protected Class<? extends AbstractVerticle> verticle() {
    return VertxRxCircuitBreakerWebTestServer.class;
  }

  public static class VertxRxCircuitBreakerWebTestServer extends AbstractVertxRxVerticle {
    CircuitBreaker breaker;

    @Override
    void handle(RoutingContext ctx, ServerEndpoint endpoint, Runnable action) {
      breaker.executeCommand(
          future -> future.complete(endpoint),
          result -> {
            if (result.failed()) {
              throw new IllegalStateException(result.cause());
            }
            controller(endpoint, action::run);
          });
    }

    @Override
    public void start(Future<Void> startFuture) {
      int port = config().getInteger(CONFIG_HTTP_SERVER_PORT);
      Router router = Router.router(vertx);
      breaker =
          CircuitBreaker.create(
              "my-circuit-breaker",
              vertx,
              // Disable the timeout otherwise it makes each test take this long.
              new CircuitBreakerOptions().setTimeout(-1));

      configure(router);
      router
          .route(EXCEPTION.getPath())
          .handler(
              ctx ->
                  breaker.executeCommand(
                      future -> future.fail(new IllegalStateException(EXCEPTION.getBody())),
                      result -> {
                        try {
                          Throwable cause = result.cause();
                          controller(
                              EXCEPTION,
                              () -> {
                                throw cause;
                              });
                        } catch (Throwable throwable) {
                          ctx.response()
                              .setStatusCode(EXCEPTION.getStatus())
                              .end(throwable.getMessage());
                        }
                      }));

      vertx
          .createHttpServer()
          .requestHandler(router::accept)
          .listen(port, httpServerAsyncResult -> startFuture.complete());
    }
  }
}
