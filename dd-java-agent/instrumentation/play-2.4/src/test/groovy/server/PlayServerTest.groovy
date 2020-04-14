package server

import datadog.opentracing.DDSpan
import datadog.trace.agent.test.asserts.TraceAssert
import datadog.trace.agent.test.base.HttpServerTest
import datadog.trace.api.DDSpanTypes
import datadog.trace.api.DDTags
import datadog.trace.bootstrap.instrumentation.api.Tags
import datadog.trace.instrumentation.netty40.server.NettyHttpServerDecorator
import datadog.trace.instrumentation.play24.PlayHttpServerDecorator
import play.mvc.Results
import play.routing.RoutingDsl
import play.server.Server

import java.util.function.Supplier

import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.ERROR
import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.EXCEPTION
import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.QUERY_PARAM
import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.REDIRECT
import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.SUCCESS

class PlayServerTest extends HttpServerTest<Server> {
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
  String component() {
    return NettyHttpServerDecorator.DECORATE.component()
  }

  @Override
  String expectedOperationName() {
    return "netty.request"
  }

  @Override
  boolean hasHandlerSpan() {
    true
  }

  @Override
  // Return the handler span's name
  String reorderHandlerSpan() {
    "play.request"
  }

  boolean testExceptionBody() {
    // I can't figure out how to set a proper exception handler to customize the response body.
    false
  }

  @Override
  void handlerSpan(TraceAssert trace, int index, Object parent, ServerEndpoint endpoint = SUCCESS) {
    trace.span(index) {
      serviceName expectedServiceName()
      operationName "play.request"
      spanType DDSpanTypes.HTTP_SERVER
      errored endpoint == ERROR || endpoint == EXCEPTION
      childOf(parent as DDSpan)
      tags {
        "$Tags.COMPONENT" PlayHttpServerDecorator.DECORATE.component()
        "$Tags.SPAN_KIND" Tags.SPAN_KIND_SERVER
        "$Tags.PEER_HOST_IPV4" { it == null || it == "127.0.0.1" } // Optional
        "$Tags.HTTP_URL" String
        "$Tags.HTTP_METHOD" String
        "$Tags.HTTP_STATUS" Integer
        if (endpoint == EXCEPTION) {
          errorTags(Exception, EXCEPTION.body)
        }
        if (endpoint.query) {
          "$DDTags.HTTP_QUERY" endpoint.query
        }
        defaultTags()
      }
    }
  }
}
