import datadog.trace.api.Trace;
import io.vertx.circuitbreaker.CircuitBreakerOptions;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.circuitbreaker.CircuitBreaker;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.core.buffer.Buffer;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.client.WebClient;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class VertxRxWebTestServer extends AbstractVerticle {
  public static final String CONFIG_HTTP_SERVER_PORT = "http.server.port";

  public static Vertx start(final int port) throws ExecutionException, InterruptedException {
    /* This is highly against Vertx ideas, but our tests are synchronous
    so we have to make sure server is up and running */
    final CompletableFuture<Void> future = new CompletableFuture<>();

    final Vertx vertx = Vertx.vertx(new VertxOptions().setClusterPort(port));

    vertx.deployVerticle(
        VertxRxWebTestServer.class.getName(),
        new DeploymentOptions()
            .setConfig(new JsonObject().put(CONFIG_HTTP_SERVER_PORT, port))
            .setInstances(3),
        res -> {
          if (!res.succeeded()) {
            throw new RuntimeException("Cannot deploy server Verticle", res.cause());
          }
          future.complete(null);
        });

    future.get();

    return vertx;
  }

  @Override
  public void start(final Future<Void> startFuture) {
    //    final io.vertx.reactivex.core.Vertx vertx = new io.vertx.reactivex.core.Vertx(this.vertx);
    final WebClient client = WebClient.create(vertx);

    final int port = config().getInteger(CONFIG_HTTP_SERVER_PORT);

    final Router router = Router.router(vertx);
    final CircuitBreaker breaker =
        CircuitBreaker.create(
            "my-circuit-breaker",
            vertx,
            new CircuitBreakerOptions()
                .setMaxFailures(5) // number of failure before opening the circuit
                .setTimeout(2000) // consider a failure if the operation does not succeed in time
                //        .setFallbackOnFailure(true) // do we call the fallback on failure
                .setResetTimeout(10000) // time spent in open state before attempting to re-try
            );

    router
        .route("/")
        .handler(
            routingContext -> {
              routingContext.response().putHeader("content-type", "text/html").end("Hello World");
            });
    router
        .route("/error")
        .handler(
            routingContext -> {
              routingContext.response().setStatusCode(500).end();
            });
    router
        .route("/proxy")
        .handler(
            routingContext -> {
              breaker.execute(
                  ctx -> {
                    client
                        .get(port, "localhost", "/test")
                        .rxSendBuffer(
                            Optional.ofNullable(routingContext.getBody()).orElse(Buffer.buffer()))
                        .subscribe(
                            response -> {
                              routingContext
                                  .response()
                                  .setStatusCode(response.statusCode())
                                  .end(response.body());
                            });
                  });
            });
    router
        .route("/test")
        .handler(
            routingContext -> {
              tracedMethod();
              routingContext.next();
            })
        .blockingHandler(RoutingContext::next)
        .handler(
            routingContext -> {
              routingContext.response().putHeader("content-type", "text/html").end("Hello World");
            });

    vertx
        .createHttpServer()
        .requestHandler(router::accept)
        .listen(port, h -> startFuture.complete());
  }

  @Trace
  private void tracedMethod() {}
}
