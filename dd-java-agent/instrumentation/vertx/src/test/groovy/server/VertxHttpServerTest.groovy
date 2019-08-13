package server

import datadog.trace.agent.test.asserts.ListWriterAssert
import datadog.trace.agent.test.base.HttpServerTest
import datadog.trace.instrumentation.netty41.server.NettyHttpServerDecorator
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import io.vertx.core.AbstractVerticle
import io.vertx.core.DeploymentOptions
import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.core.VertxOptions
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router

import java.util.concurrent.CompletableFuture

import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.ERROR
import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.EXCEPTION
import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.REDIRECT
import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.SUCCESS

class VertxHttpServerTest extends HttpServerTest<Vertx, NettyHttpServerDecorator> {
  public static final String CONFIG_HTTP_SERVER_PORT = "http.server.port"

  @Override
  Vertx startServer(int port) {
    def server = Vertx.vertx(new VertxOptions()
    // Useful for debugging:
    // .setBlockedThreadCheckInterval(Integer.MAX_VALUE)
      .setClusterPort(port))
    final CompletableFuture<Void> future = new CompletableFuture<>()
    server.deployVerticle(verticle().name,
      new DeploymentOptions()
        .setConfig(new JsonObject().put(CONFIG_HTTP_SERVER_PORT, port))
        .setInstances(3)) { res ->
      if (!res.succeeded()) {
        throw new RuntimeException("Cannot deploy server Verticle", res.cause())
      }
      future.complete(null)
    }

    future.get()
    return server
  }

  protected Class<io.vertx.reactivex.core.AbstractVerticle> verticle() {
    return VertxWebTestServer
  }

  @Override
  void stopServer(Vertx server) {
    server.close()
  }

  @Override
  NettyHttpServerDecorator decorator() {
    return NettyHttpServerDecorator.DECORATE
  }

  @Override
  String expectedOperationName() {
    "netty.request"
  }

  @Override
  boolean testExceptionBody() {
    false
  }

  static class VertxWebTestServer extends AbstractVerticle {

    @Override
    void start(final Future<Void> startFuture) {
      final int port = config().getInteger(CONFIG_HTTP_SERVER_PORT)
      final Router router = Router.router(vertx)

      router.route(SUCCESS.path).handler { ctx ->
        controller(SUCCESS) {
          ctx.response().setStatusCode(SUCCESS.status).end(SUCCESS.body)
        }
      }
      router.route(REDIRECT.path).handler { ctx ->
        controller(REDIRECT) {
          ctx.response().setStatusCode(REDIRECT.status).putHeader("location", REDIRECT.body).end()
        }
      }
      router.route(ERROR.path).handler { ctx ->
        controller(ERROR) {
          ctx.response().setStatusCode(ERROR.status).end(ERROR.body)
        }
      }
      router.route(EXCEPTION.path).handler { ctx ->
        controller(EXCEPTION) {
          throw new Exception(EXCEPTION.body)
        }
      }

      vertx.createHttpServer()
        .requestHandler { router.accept(it) }
        .listen(port) { startFuture.complete() }
    }
  }

  void cleanAndAssertTraces(
    final int size,
    @ClosureParams(value = SimpleType, options = "datadog.trace.agent.test.asserts.ListWriterAssert")
    @DelegatesTo(value = ListWriterAssert, strategy = Closure.DELEGATE_FIRST)
    final Closure spec) {
    // If this is failing, make sure HttpServerTestAdvice is applied correctly.
    TEST_WRITER.waitForTraces(size * 2)

    // Netty closes the parent span before the controller returns, so we need to manually reorder it.
    TEST_WRITER.each {
      def controllerSpan = it.find {
        it.operationName == "controller"
      }
      if (controllerSpan) {
        it.remove(controllerSpan)
        it.add(controllerSpan)
      }
    }
    super.cleanAndAssertTraces(size, spec)
  }
}
