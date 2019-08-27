package server

import datadog.opentracing.DDSpan
import datadog.trace.agent.test.asserts.TraceAssert
import datadog.trace.agent.test.base.HttpServerTest
import datadog.trace.api.DDSpanTypes
import datadog.trace.instrumentation.akkahttp.AkkaHttpServerDecorator
import datadog.trace.instrumentation.play26.PlayHttpServerDecorator
import io.opentracing.tag.Tags
import play.BuiltInComponents
import play.Mode
import play.mvc.Results
import play.routing.RoutingDsl
import play.server.Server

import java.util.function.Supplier

import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.ERROR
import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.EXCEPTION
import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.REDIRECT
import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.SUCCESS

class PlayServerTest extends HttpServerTest<Server, AkkaHttpServerDecorator> {
  @Override
  Server startServer(int port) {
    return Server.forRouter(Mode.TEST, port) { BuiltInComponents components ->
      RoutingDsl.fromComponents(components)
        .GET(SUCCESS.getPath()).routeTo({
        controller(SUCCESS) {
          Results.status(SUCCESS.getStatus(), SUCCESS.getBody())
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
        .build()
    }
  }

  @Override
  void stopServer(Server server) {
    server.stop()
  }

  @Override
  AkkaHttpServerDecorator decorator() {
    return AkkaHttpServerDecorator.DECORATE
  }

  @Override
  String expectedOperationName() {
    return "akka-http.request"
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
        "$Tags.COMPONENT.key" PlayHttpServerDecorator.DECORATE.component()
        "$Tags.HTTP_STATUS.key" Integer
        "$Tags.HTTP_URL.key" String
        "$Tags.PEER_HOST_IPV4.key" { it == null || it == "127.0.0.1" } // Optional
        "$Tags.HTTP_METHOD.key" String
        "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_SERVER
        defaultTags()
        if (endpoint == ERROR) {
          "$Tags.ERROR.key" true
        } else if (endpoint == EXCEPTION) {
          errorTags(Exception, EXCEPTION.body)
        }
      }
    }
  }

  void serverSpan(TraceAssert trace, int index, String traceID = null, String parentID = null, String method = "GET", ServerEndpoint endpoint = SUCCESS) {
    trace.span(index) {
      serviceName expectedServiceName()
      operationName expectedOperationName()
      resourceName endpoint.status == 404 ? "404" : "$method ${endpoint.resolve(address).path}"
      spanType DDSpanTypes.HTTP_SERVER
      errored endpoint.errored
      if (parentID != null) {
        traceId traceID
        parentId parentID
      } else {
        parent()
      }
      tags {
        defaultTags(true)
        "$Tags.COMPONENT.key" serverDecorator.component()
        if (endpoint.errored) {
          "$Tags.ERROR.key" endpoint.errored
          "error.msg" { it == null || it == EXCEPTION.body }
          "error.type" { it == null || it == Exception.name }
          "error.stack" { it == null || it instanceof String }
        }
        "$Tags.HTTP_STATUS.key" endpoint.status
        "$Tags.HTTP_URL.key" "${endpoint.resolve(address)}"
        "$Tags.HTTP_METHOD.key" method
        "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_SERVER
      }
    }
  }
}
