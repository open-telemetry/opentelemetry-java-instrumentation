package server

import io.opentelemetry.auto.api.MoreTags
import io.opentelemetry.auto.api.SpanTypes
import io.opentelemetry.auto.instrumentation.api.Tags
import io.opentelemetry.auto.instrumentation.netty41.server.NettyHttpServerDecorator
import io.opentelemetry.auto.instrumentation.ratpack.RatpackServerDecorator
import io.opentelemetry.auto.test.asserts.TraceAssert
import io.opentelemetry.auto.test.base.HttpServerTest
import io.opentelemetry.sdk.trace.SpanData
import ratpack.error.ServerErrorHandler
import ratpack.groovy.test.embed.GroovyEmbeddedApp
import ratpack.handling.Context
import ratpack.test.embed.EmbeddedApp

import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.ERROR
import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.EXCEPTION
import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.QUERY_PARAM
import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.REDIRECT
import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.SUCCESS

class RatpackHttpServerTest extends HttpServerTest<EmbeddedApp, NettyHttpServerDecorator> {

  @Override
  EmbeddedApp startServer(int bindPort) {
    def ratpack = GroovyEmbeddedApp.ratpack {
      serverConfig {
        port bindPort
      }
      bindings {
        bind TestErrorHandler
      }
      handlers {
        prefix(SUCCESS.rawPath()) {
          all {
            controller(SUCCESS) {
              context.response.status(SUCCESS.status).send(SUCCESS.body)
            }
          }
        }
        prefix(QUERY_PARAM.rawPath()) {
          all {
            controller(QUERY_PARAM) {
              context.response.status(QUERY_PARAM.status).send(request.query)
            }
          }
        }
        prefix(REDIRECT.rawPath()) {
          all {
            controller(REDIRECT) {
              context.redirect(REDIRECT.body)
            }
          }
        }
        prefix(ERROR.rawPath()) {
          all {
            controller(ERROR) {
              context.response.status(ERROR.status).send(ERROR.body)
            }
          }
        }
        prefix(EXCEPTION.rawPath()) {
          all {
            controller(EXCEPTION) {
              throw new Exception(EXCEPTION.body)
            }
          }
        }
      }
    }
    ratpack.server.start()

    assert ratpack.address.port == bindPort
    return ratpack
  }

  static class TestErrorHandler implements ServerErrorHandler {
    @Override
    void error(Context context, Throwable throwable) throws Exception {
      context.response.status(500).send(throwable.message)
    }
  }

  @Override
  void stopServer(EmbeddedApp server) {
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
  boolean hasHandlerSpan() {
    true
  }

  @Override
  void handlerSpan(TraceAssert trace, int index, Object parent, String method = "GET", ServerEndpoint endpoint = SUCCESS) {
    trace.span(index) {
      operationName "ratpack.handler"
      errored endpoint == ERROR || endpoint == EXCEPTION
      childOf((SpanData) parent)
      tags {
        "$MoreTags.RESOURCE_NAME" endpoint.status == 404 ? "$method /" : "$method ${endpoint.path}"
        "$MoreTags.SPAN_TYPE" SpanTypes.HTTP_SERVER
        "$Tags.COMPONENT" RatpackServerDecorator.DECORATE.component()
        "$Tags.SPAN_KIND" Tags.SPAN_KIND_SERVER
        "$Tags.PEER_HOSTNAME" "localhost"
        "$Tags.PEER_HOST_IPV4" { it == null || it == "127.0.0.1" } // Optional
        "$Tags.PEER_PORT" Long
        "$Tags.HTTP_URL" String
        "$Tags.HTTP_METHOD" String
        "$Tags.HTTP_STATUS" Long
        if (endpoint == EXCEPTION) {
          errorTags(Exception, EXCEPTION.body)
        }
        if (endpoint.query) {
          "$MoreTags.HTTP_QUERY" endpoint.query
        }
      }
    }
  }

  void serverSpan(TraceAssert trace, int index, String traceID = null, String parentID = null, String method = "GET", ServerEndpoint endpoint = SUCCESS) {
    trace.span(index) {
      operationName expectedOperationName()
      errored endpoint.errored
      if (parentID != null) {
        traceId traceID
        parentId parentID
      } else {
        parent()
      }
      tags {
        "$MoreTags.RESOURCE_NAME" endpoint.status == 404 ? "$method /" : "$method ${endpoint.path}"
        "$MoreTags.SPAN_TYPE" SpanTypes.HTTP_SERVER
        "$Tags.COMPONENT" serverDecorator.component()
        "$Tags.SPAN_KIND" Tags.SPAN_KIND_SERVER
        "$Tags.PEER_HOSTNAME" { it == "localhost" || it == "127.0.0.1" }
        "$Tags.PEER_PORT" Long
        "$Tags.PEER_HOST_IPV4" { it == null || it == "127.0.0.1" } // Optional
        "$Tags.HTTP_URL" "${endpoint.resolve(address)}"
        "$Tags.HTTP_METHOD" method
        "$Tags.HTTP_STATUS" endpoint.status
        if (endpoint.query) {
          "$MoreTags.HTTP_QUERY" endpoint.query
        }
      }
    }
  }
}
