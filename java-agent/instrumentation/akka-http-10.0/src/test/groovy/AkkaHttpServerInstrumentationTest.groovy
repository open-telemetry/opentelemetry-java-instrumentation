import io.opentelemetry.auto.api.MoreTags
import io.opentelemetry.auto.api.SpanTypes
import io.opentelemetry.auto.instrumentation.akkahttp.AkkaHttpServerDecorator
import io.opentelemetry.auto.instrumentation.api.Tags
import io.opentelemetry.auto.test.asserts.TraceAssert
import io.opentelemetry.auto.test.base.HttpServerTest

import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.EXCEPTION
import static io.opentelemetry.auto.test.base.HttpServerTest.ServerEndpoint.SUCCESS

abstract class AkkaHttpServerInstrumentationTest extends HttpServerTest<Object, AkkaHttpServerDecorator> {

  @Override
  AkkaHttpServerDecorator decorator() {
    return AkkaHttpServerDecorator.DECORATE
  }

  @Override
  String expectedOperationName() {
    return "akka-http.request"
  }

  @Override
  boolean testExceptionBody() {
    false
  }

// FIXME: This doesn't work because we don't support bindAndHandle.
//  @Override
//  def startServer(int port) {
//    AkkaHttpTestWebServer.start(port)
//  }
//
//  @Override
//  void stopServer(Object ignore) {
//    AkkaHttpTestWebServer.stop()
//  }

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
        "$MoreTags.SPAN_TYPE" SpanTypes.HTTP_SERVER
        "$Tags.COMPONENT" serverDecorator.component()
        "$Tags.SPAN_KIND" Tags.SPAN_KIND_SERVER
        "$Tags.HTTP_URL" "${endpoint.resolve(address)}"
        "$Tags.HTTP_METHOD" method
        "$Tags.HTTP_STATUS" endpoint.status
        if (endpoint.errored) {
          "error.msg" { it == null || it == EXCEPTION.body }
          "error.type" { it == null || it == Exception.name }
          "error.stack" { it == null || it instanceof String }
        }
        if (endpoint.query) {
          "$MoreTags.HTTP_QUERY" endpoint.query
        }
      }
    }
  }
}

class AkkaHttpServerInstrumentationTestSync extends AkkaHttpServerInstrumentationTest {
  @Override
  def startServer(int port) {
    AkkaHttpTestSyncWebServer.start(port)
  }

  @Override
  void stopServer(Object ignore) {
    AkkaHttpTestSyncWebServer.stop()
  }
}

class AkkaHttpServerInstrumentationTestAsync extends AkkaHttpServerInstrumentationTest {
  @Override
  def startServer(int port) {
    AkkaHttpTestAsyncWebServer.start(port)
  }

  @Override
  void stopServer(Object ignore) {
    AkkaHttpTestAsyncWebServer.stop()
  }
}
