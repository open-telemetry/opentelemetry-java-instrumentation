package server

import datadog.opentracing.DDSpan
import datadog.trace.agent.test.asserts.ListWriterAssert
import datadog.trace.agent.test.asserts.TraceAssert
import datadog.trace.agent.test.base.HttpServerTest
import datadog.trace.api.DDSpanTypes
import datadog.trace.instrumentation.netty41.server.NettyHttpServerDecorator
import datadog.trace.instrumentation.ratpack.RatpackServerDecorator
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import io.opentracing.tag.Tags
import ratpack.error.ServerErrorHandler
import ratpack.groovy.test.embed.GroovyEmbeddedApp
import ratpack.handling.Context
import ratpack.test.embed.EmbeddedApp

import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.ERROR
import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.EXCEPTION
import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.REDIRECT
import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.SUCCESS

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

  void cleanAndAssertTraces(
    final int size,
    @ClosureParams(value = SimpleType, options = "datadog.trace.agent.test.asserts.ListWriterAssert")
    @DelegatesTo(value = ListWriterAssert, strategy = Closure.DELEGATE_FIRST)
    final Closure spec) {
    // If this is failing, make sure HttpServerTestAdvice is applied correctly.
    TEST_WRITER.waitForTraces(size * 2)

    // Ratpack closes the handler span before the controller returns, so we need to manually reorder it.
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

  @Override
  void handlerSpan(TraceAssert trace, int index, Object parent, ServerEndpoint endpoint = SUCCESS) {
    trace.span(index) {
      serviceName expectedServiceName()
      operationName "ratpack.handler"
      spanType DDSpanTypes.HTTP_SERVER
      errored endpoint == ERROR || endpoint == EXCEPTION
      childOf(parent as DDSpan)
      tags {
        "$Tags.COMPONENT.key" RatpackServerDecorator.DECORATE.component()
        "$Tags.HTTP_STATUS.key" Integer
        "$Tags.HTTP_URL.key" String
        "$Tags.PEER_HOSTNAME.key" "localhost"
        "$Tags.PEER_PORT.key" Integer
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
}
