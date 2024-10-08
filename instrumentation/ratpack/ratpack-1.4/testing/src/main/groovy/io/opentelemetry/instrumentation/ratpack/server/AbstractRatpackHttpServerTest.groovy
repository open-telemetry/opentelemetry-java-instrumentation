/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ratpack.server

import io.netty.buffer.ByteBuf
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.instrumentation.test.asserts.TraceAssert
import io.opentelemetry.instrumentation.test.base.HttpServerTest
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint
import io.opentelemetry.sdk.trace.data.SpanData
import org.junit.Assume
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import ratpack.error.ServerErrorHandler
import ratpack.handling.Context
import ratpack.server.RatpackServer
import ratpack.server.RatpackServerSpec

import static io.opentelemetry.api.trace.SpanKind.INTERNAL
import static io.opentelemetry.api.trace.SpanKind.SERVER
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.CAPTURE_HEADERS
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.ERROR
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.EXCEPTION
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.INDEXED_CHILD
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.PATH_PARAM
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.QUERY_PARAM
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.REDIRECT
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.SUCCESS

abstract class AbstractRatpackHttpServerTest extends HttpServerTest<RatpackServer> {

  protected static final ServerEndpoint POST_STREAM =
      new ServerEndpoint(
          "POST_STREAM",
          "post-stream",
          SUCCESS.getStatus(),
          SUCCESS.getBody(),
          false)

  abstract void configure(RatpackServerSpec serverSpec)

  @Override
  RatpackServer startServer(int bindPort) {
    def ratpack = RatpackServer.start {
      it.serverConfig {
        it.port(bindPort)
        it.address(InetAddress.getByName("localhost"))
      }
      it.handlers {
        it.register {
          it.add(ServerErrorHandler, new TestErrorHandler())
        }
        it.prefix(SUCCESS.rawPath()) {
          it.all { context ->
            controller(SUCCESS) {
              context.response.status(SUCCESS.status).send(SUCCESS.body)
            }
          }
        }
        it.prefix(INDEXED_CHILD.rawPath()) {
          it.all { context ->
            controller(INDEXED_CHILD) {
              INDEXED_CHILD.collectSpanAttributes { context.request.queryParams.get(it) }
              context.response.status(INDEXED_CHILD.status).send()
            }
          }
        }
        it.prefix(QUERY_PARAM.rawPath()) {
          it.all { context ->
            controller(QUERY_PARAM) {
              context.response.status(QUERY_PARAM.status).send(context.request.query)
            }
          }
        }
        it.prefix(REDIRECT.rawPath()) {
          it.all { context ->
            controller(REDIRECT) {
              context.redirect(REDIRECT.body)
            }
          }
        }
        it.prefix(ERROR.rawPath()) {
          it.all { context ->
            controller(ERROR) {
              context.response.status(ERROR.status).send(ERROR.body)
            }
          }
        }
        it.prefix(EXCEPTION.rawPath()) {
          it.all {
            controller(EXCEPTION) {
              throw new Exception(EXCEPTION.body)
            }
          }
        }
        it.prefix("path/:id/param") {
          it.all { context ->
            controller(PATH_PARAM) {
              context.response.status(PATH_PARAM.status).send(context.pathTokens.id)
            }
          }
        }
        it.prefix(CAPTURE_HEADERS.rawPath()) {
          it.all { context ->
            controller(CAPTURE_HEADERS) {
              context.response.status(CAPTURE_HEADERS.status)
              context.response.headers.set("X-Test-Response", context.request.headers.get("X-Test-Request"))
              context.response.send(CAPTURE_HEADERS.body)
            }
          }
        }
        it.prefix(POST_STREAM.rawPath()) {
          it.all { context ->
            handlePostStream(context)
          }
        }
      }
      configure(it)
    }

    assert ratpack.bindPort == bindPort
    return ratpack
  }

  def handlePostStream(context) {
    controller(POST_STREAM) {
      context.request.bodyStream.subscribe(new Subscriber<ByteBuf>() {
        private Subscription subscription
        private int count
        private String traceId

        @Override
        void onSubscribe(Subscription subscription) {
          this.subscription = subscription
          traceId = Span.current().getSpanContext().getTraceId()
          subscription.request(1)
        }

        @Override
        void onNext(ByteBuf byteBuf) {
          assert traceId == Span.current().getSpanContext().getTraceId()
          if (count < 2) {
            runWithSpan("onNext") {
              count++
            }
          }
          byteBuf.release()
          subscription.request(1)
        }

        @Override
        void onError(Throwable throwable) {
          // prints the assertion error from onNext
          throwable.printStackTrace()
          context.response.status(500).send(throwable.message)
        }

        @Override
        void onComplete() {
          runWithSpan("onComplete") {
            context.response.status(200).send(POST_STREAM.body)
          }
        }
      })
    }
  }

  // TODO(anuraaga): The default Ratpack error handler also returns a 500 which is all we test, so
  // we don't actually have test coverage ensuring our instrumentation correctly delegates to this
  // user registered handler.
  static class TestErrorHandler implements ServerErrorHandler {
    @Override
    void error(Context context, Throwable throwable) throws Exception {
      context.response.status(500).send(throwable.message)
    }
  }

  @Override
  void stopServer(RatpackServer server) {
    server.stop()
  }

  @Override
  boolean hasHandlerSpan(ServerEndpoint endpoint) {
    true
  }

  @Override
  boolean testPathParam() {
    true
  }

  @Override
  boolean verifyServerSpanEndTime() {
    // server spans are ended inside of the controller spans
    return false
  }

  @Override
  void handlerSpan(TraceAssert trace, int index, Object parent, String method = "GET", ServerEndpoint endpoint = SUCCESS) {
    trace.span(index) {
      name endpoint.status == 404 ? "/" : endpoint == PATH_PARAM ? "/path/:id/param" : endpoint.path
      kind INTERNAL
      childOf((SpanData) parent)
      if (endpoint == EXCEPTION) {
        status StatusCode.ERROR
        errorEvent(Exception, EXCEPTION.body)
      }
    }
  }

  @Override
  String expectedHttpRoute(ServerEndpoint endpoint, String method) {
    return endpoint.status == 404 ? "/" : endpoint == PATH_PARAM ? "/path/:id/param" : endpoint.path
  }

  boolean testPostStream() {
    true
  }

  def "test post stream"() {
    Assume.assumeTrue(testPostStream())

    when:
    // body should be large enough to trigger multiple calls to onNext
    def body = "foobar" * 10000
    def response = client.post(resolveAddress(POST_STREAM), body).aggregate().join()

    then:
    response.status().code() == POST_STREAM.status
    response.contentUtf8() == POST_STREAM.body

    def hasHandlerSpan = hasHandlerSpan(POST_STREAM)
    // when using javaagent instrumentation the parent of reactive callbacks is the controller span
    // where subscribe was called, for library instrumentation server span is the parent
    def reactiveCallbackParent = hasHandlerSpan ? 2 : 0
    assertTraces(1) {
      trace(0, 5 + (hasHandlerSpan ? 1 : 0)) {
        span(0) {
          name "POST /post-stream"
          kind SERVER
          hasNoParent()
        }
        if (hasHandlerSpan) {
          span(1) {
            name "/post-stream"
            childOf span(0)
          }
        }
        def offset = hasHandlerSpan ? 1 : 0
        span(1 + offset) {
          name "controller"
          childOf span(offset)
        }
        span(2 + offset) {
          name "onNext"
          childOf span(reactiveCallbackParent)
        }
        span(3 + offset) {
          name "onNext"
          childOf span(reactiveCallbackParent)
        }
        span(4 + offset) {
          name "onComplete"
          childOf span(reactiveCallbackParent)
        }
      }
    }
  }
}
