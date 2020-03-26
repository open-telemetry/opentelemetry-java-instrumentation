package server

import datadog.opentracing.DDSpan
import datadog.trace.agent.test.asserts.TraceAssert
import datadog.trace.agent.test.base.HttpServerTest
import datadog.trace.api.DDSpanTypes
import datadog.trace.api.DDTags
import datadog.trace.bootstrap.instrumentation.api.Tags
import datadog.trace.instrumentation.netty38.server.NettyHttpServerDecorator
import datadog.trace.instrumentation.play23.PlayHttpServerDecorator
import play.api.test.TestServer

import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.*

class PlayServerTest extends HttpServerTest<TestServer> {
  @Override
  TestServer startServer(int port) {
    def server = SyncServer.server(port)
    server.start()
    return server
  }

  @Override
  void stopServer(TestServer server) {
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

  // We don't have instrumentation for this version of netty yet
  @Override
  boolean hasHandlerSpan() {
    true
  }

  @Override
  // Return the handler span's name
  String reorderHandlerSpan() {
    "play.request"
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
        if (endpoint == ERROR) {
          "$Tags.ERROR" true
        } else if (endpoint == EXCEPTION) {
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
