import datadog.trace.api.Trace;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class VertxWebTestServer extends AbstractVerticle {
  public static final String CONFIG_HTTP_SERVER_PORT = "http.server.port";

  public static Vertx start(final int port) throws ExecutionException, InterruptedException {
    /* This is highly against Vertx ideas, but our tests are synchronous
    so we have to make sure server is up and running */
    final CompletableFuture<Void> future = new CompletableFuture<>();

    final Vertx vertx = Vertx.vertx(new VertxOptions().setClusterPort(port));

    vertx.deployVerticle(
        VertxWebTestServer.class.getName(),
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
    final HttpClient client = vertx.createHttpClient();

    final int port = config().getInteger(CONFIG_HTTP_SERVER_PORT);

    final Router router = Router.router(vertx);

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
              client
                  .get(
                      port,
                      "localhost",
                      "/test",
                      response -> {
                        response.bodyHandler(
                            buffer -> {
                              routingContext
                                  .response()
                                  .setStatusCode(response.statusCode())
                                  .end(buffer);
                            });
                      })
                  .end(Optional.ofNullable(routingContext.getBody()).orElse(Buffer.buffer()));
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
