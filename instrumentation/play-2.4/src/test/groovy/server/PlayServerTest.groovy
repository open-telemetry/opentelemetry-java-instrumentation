package server

import io.opentelemetry.auto.api.MoreTags
import io.opentelemetry.auto.api.SpanTypes
import io.opentelemetry.auto.instrumentation.api.Tags
import io.opentelemetry.auto.instrumentation.netty40.server.NettyHttpServerDecorator
import io.opentelemetry.auto.instrumentation.play24.PlayHttpServerDecorator
import io.opentelemetry.auto.test.asserts.TraceAssert
import io.opentelemetry.auto.test.base.HttpServerTest
import io.opentelemetry.sdk.trace.SpanData
import play.mvc.Results
import play.routing.RoutingDsl
import play.server.Server

import java.util.function.Supplier

import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.ERROR
import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.EXCEPTION
import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.QUERY_PARAM
import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.REDIRECT
import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.SUCCESS

class PlayServerTest extends HttpServerTest<Server, NettyHttpServerDecorator> {
  @Override
  Server startServer(int port) {
    def router =
      new RoutingDsl()
        .GET(SUCCESS.getPath()).routeTo({
        controller(SUCCESS) {
          Results.status(SUCCESS.getStatus(), SUCCESS.getBody())
        }
      } as Supplier)
        .GET(QUERY_PARAM.getPath()).routeTo({
        controller(QUERY_PARAM) {
          Results.status(QUERY_PARAM.getStatus(), QUERY_PARAM.getBody())
        }
      } as Supplier)
        .GET(REDIRECT.getPath()).routeTo({
        controller(REDIRECT) {
          Results.found(REDIRECT.getBody())
        }
      } as Supplier)
        .GET(ERROR.getPath()).routeTo({
        controller(ERROR) {
          Results.status(ERROR.getStatus(), ERROR.getBody())
        }
      } as Supplier)
        .GET(EXCEPTION.getPath()).routeTo({
        controller(EXCEPTION) {
          throw new Exception(EXCEPTION.getBody())
        }
      } as Supplier)

    return Server.forRouter(router.build(), port)
  }

  @Override
  void stopServer(Server server) {
    server.stop()
  }

  @Override
  NettyHttpServerDecorator decorator() {
    return NettyHttpServerDecorator.DECORATE
  }

  @Override
  String expectedOperationName() {
    return "netty.request"
  }

  @Override
  boolean hasHandlerSpan() {
    true
  }

  boolean testExceptionBody() {
    // I can't figure out how to set a proper exception handler to customize the response body.
    false
  }

  @Override
  void handlerSpan(TraceAssert trace, int index, Object parent, String method = "GET", ServerEndpoint endpoint = SUCCESS) {
    trace.span(index) {
      operationName "play.request"
      errored endpoint == ERROR || endpoint == EXCEPTION
      childOf((SpanData) parent)
      tags {
        "$MoreTags.SPAN_TYPE" SpanTypes.HTTP_SERVER
        "$Tags.COMPONENT" PlayHttpServerDecorator.DECORATE.component()
        "$Tags.SPAN_KIND" Tags.SPAN_KIND_SERVER
        "$Tags.PEER_HOST_IPV4" { it == null || it == "127.0.0.1" } // Optional
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
}
