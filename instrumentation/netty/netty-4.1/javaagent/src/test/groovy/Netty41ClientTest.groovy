/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.netty.bootstrap.Bootstrap
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.ChannelPipeline
import io.netty.channel.EventLoopGroup
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.http.DefaultFullHttpRequest
import io.netty.handler.codec.http.HttpClientCodec
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpVersion
import io.netty.handler.ssl.SslContext
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.timeout.ReadTimeoutHandler
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.base.HttpClientTest
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest
import io.opentelemetry.instrumentation.testing.junit.http.SingleConnection
import io.opentelemetry.javaagent.instrumentation.netty.v4_1.client.HttpClientTracingHandler
import spock.lang.Shared
import spock.lang.Unroll

import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

import static org.junit.jupiter.api.Assumptions.assumeTrue

@Unroll
class Netty41ClientTest extends HttpClientTest<DefaultFullHttpRequest> implements AgentTestTrait {

  @Shared
  private EventLoopGroup eventLoopGroup = buildEventLoopGroup()

  @Shared
  private Bootstrap bootstrap = buildBootstrap(false)

  @Shared
  private Bootstrap httpsBootstrap = buildBootstrap(true)

  @Shared
  private Bootstrap readTimeoutBootstrap = buildBootstrap(false, true)

  def cleanupSpec() {
    eventLoopGroup?.shutdownGracefully()
  }

  Bootstrap buildBootstrap(boolean https, boolean readTimeout = false) {
    Bootstrap bootstrap = new Bootstrap()
    bootstrap.group(eventLoopGroup)
      .channel(getChannelClass())
      .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT_MS)
      .handler(new ChannelInitializer<SocketChannel>() {
        @Override
        protected void initChannel(SocketChannel socketChannel) throws Exception {
          ChannelPipeline pipeline = socketChannel.pipeline()
          if (https) {
            SslContext sslContext = SslContextBuilder.forClient().build()
            pipeline.addLast(sslContext.newHandler(socketChannel.alloc()))
          }
          if (readTimeout) {
            pipeline.addLast(new ReadTimeoutHandler(READ_TIMEOUT_MS, TimeUnit.MILLISECONDS))
          }
          pipeline.addLast(new HttpClientCodec())
        }
      })

    return bootstrap
  }

  EventLoopGroup buildEventLoopGroup() {
    return new NioEventLoopGroup()
  }

  Class<Channel> getChannelClass() {
    return NioSocketChannel
  }

  Bootstrap getBootstrap(URI uri) {
    if (uri.getScheme() == "https") {
      return httpsBootstrap
    } else if (uri.getPath() == "/read-timeout") {
      return readTimeoutBootstrap
    }
    return bootstrap
  }

  @Override
  DefaultFullHttpRequest buildRequest(String method, URI uri, Map<String, String> headers) {
    def target = uri.path
    if (uri.query != null) {
      target += "?" + uri.query
    }
    def request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.valueOf(method), target, Unpooled.EMPTY_BUFFER)
    request.headers().set(HttpHeaderNames.HOST, uri.host + ":" + uri.port)
    headers.each { k, v -> request.headers().set(k, v) }
    return request
  }

  @Override
  int sendRequest(DefaultFullHttpRequest request, String method, URI uri, Map<String, String> headers) {
    def channel = getBootstrap(uri).connect(uri.host, getPort(uri)).sync().channel()
    def result = new CompletableFuture<Integer>()
    channel.pipeline().addLast(new ClientHandler(result))
    channel.writeAndFlush(request).get()
    return result.get(20, TimeUnit.SECONDS)
  }

  @Override
  void sendRequestWithCallback(DefaultFullHttpRequest request, String method, URI uri, Map<String, String> headers, AbstractHttpClientTest.RequestResult requestResult) {
    Channel ch
    try {
      ch = getBootstrap(uri).connect(uri.host, getPort(uri)).sync().channel()
    } catch (Exception exception) {
      requestResult.complete(exception)
      return
    }
    def result = new CompletableFuture<Integer>()
    result.whenComplete { status, throwable ->
      requestResult.complete({ status }, throwable)
    }
    ch.pipeline().addLast(new ClientHandler(result))
    ch.writeAndFlush(request)
  }

  @Override
  String expectedClientSpanName(URI uri, String method) {
    switch (uri.toString()) {
      case "http://localhost:61/": // unopened port
      case "https://192.0.2.1/": // non routable address
        return "CONNECT"
      default:
        return super.expectedClientSpanName(uri, method)
    }
  }

  @Override
  Set<AttributeKey<?>> httpAttributes(URI uri) {
    switch (uri.toString()) {
      case "http://localhost:61/": // unopened port
      case "https://192.0.2.1/": // non routable address
        return []
    }
    return super.httpAttributes(uri)
  }

  @Override
  boolean testRedirects() {
    false
  }

  @Override
  boolean testReadTimeout() {
    true
  }

  def "test connection reuse and second request with lazy execute"() {
    setup:
    //Create a simple Netty pipeline
    EventLoopGroup group = new NioEventLoopGroup()
    Bootstrap b = new Bootstrap()
    b.group(group)
      .channel(NioSocketChannel)
      .handler(new ChannelInitializer<SocketChannel>() {
        @Override
        protected void initChannel(SocketChannel socketChannel) throws Exception {
          ChannelPipeline pipeline = socketChannel.pipeline()
          pipeline.addLast(new HttpClientCodec())
        }
      })
    def request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/success", Unpooled.EMPTY_BUFFER)
    request.headers().set(HttpHeaderNames.HOST, "localhost:" + server.httpPort())
    Channel ch = null

    when:
    // note that this is a purely asynchronous request
    runWithSpan("parent1") {
      ch = b.connect("localhost", server.httpPort()).sync().channel()
      ch.write(request)
      ch.flush()
    }
    // This is a cheap/easy way to block/ensure that the first request has finished and check reported spans midway through
    // the complex sequence of events
    assertTraces(1) {
      trace(0, 3) {
        span(0) {
          name "parent1"
          kind SpanKind.INTERNAL
          hasNoParent()
        }
        clientSpan(it, 1, span(0))
        serverSpan(it, 2, span(1))
      }
    }

    then:
    // now run a second request through the same channel
    runWithSpan("parent2") {
      ch.write(request)
      ch.flush()
    }

    assertTraces(2) {
      trace(0, 3) {
        span(0) {
          name "parent1"
          kind SpanKind.INTERNAL
          hasNoParent()
        }
        clientSpan(it, 1, span(0))
        serverSpan(it, 2, span(1))
      }
      trace(1, 3) {
        span(0) {
          name "parent2"
          kind SpanKind.INTERNAL
          hasNoParent()
        }
        clientSpan(it, 1, span(0))
        serverSpan(it, 2, span(1))
      }
    }


    cleanup:
    group.shutdownGracefully()
  }

  def "when a handler is added to the netty pipeline we add our tracing handler"() {
    setup:
    def channel = new EmbeddedChannel()
    def pipeline = channel.pipeline()

    when:
    pipeline.addLast("name", new HttpClientCodec())

    then:
    // The first one returns the removed tracing handler
    pipeline.remove(HttpClientTracingHandler.getName()) != null
  }

  def "when a handler is added to the netty pipeline we add ONLY ONE tracing handler"() {
    setup:
    def channel = new EmbeddedChannel()
    def pipeline = channel.pipeline()

    when:
    pipeline.addLast("name", new HttpClientCodec())
    // The first one returns the removed tracing handler
    pipeline.remove(HttpClientTracingHandler.getName())
    // There is only one
    pipeline.remove(HttpClientTracingHandler.getName()) == null

    then:
    thrown NoSuchElementException
  }

  def "handlers of different types can be added"() {
    setup:
    def channel = new EmbeddedChannel()
    def pipeline = channel.pipeline()

    when:
    pipeline.addLast("some_handler", new SimpleHandler())
    pipeline.addLast("a_traced_handler", new HttpClientCodec())

    then:
    // The first one returns the removed tracing handler
    null != pipeline.remove(HttpClientTracingHandler.getName())
    null != pipeline.remove("some_handler")
    null != pipeline.remove("a_traced_handler")
  }

  def "calling pipeline.addLast methods that use overloaded methods does not cause infinite loop"() {
    setup:
    def channel = new EmbeddedChannel()

    when:
    channel.pipeline().addLast(new SimpleHandler(), new OtherSimpleHandler())

    then:
    null != channel.pipeline().remove('Netty41ClientTest$SimpleHandler#0')
    null != channel.pipeline().remove('Netty41ClientTest$OtherSimpleHandler#0')
  }

  def "when a traced handler is added from an initializer we still detect it and add our channel handlers"() {
    // This test method replicates a scenario similar to how reactor 0.8.x register the `HttpClientCodec` handler
    // into the pipeline.
    setup:
    assumeTrue(Boolean.getBoolean("testLatestDeps"))
    def channel = new EmbeddedChannel()

    when:
    channel.pipeline().addLast(new TracedHandlerFromInitializerHandler())

    then:
    null != channel.pipeline().get(HttpClientTracingHandler.getName())
    null != channel.pipeline().remove("added_in_initializer")
    null == channel.pipeline().get(HttpClientTracingHandler.getName())
  }

  def "request with trace annotated method #method"() {
    given:
    def annotatedClass = new TracedClass()

    when:
    def responseCode = runWithSpan("parent") {
      annotatedClass.tracedMethod(method)
    }

    then:
    responseCode == 200
    assertTraces(1) {
      trace(0, 4) {
        span(0) {
          name "parent"
          kind SpanKind.INTERNAL
          hasNoParent()
        }
        span(1) {
          childOf span(0)
          name "tracedMethod"
          attributes {
          }
        }
        clientSpan(it, 2, span(1), method)
        serverSpan(it, 3, span(2))
      }
    }

    where:
    method << BODY_METHODS
  }

  class TracedClass {
    int tracedMethod(String method) {
      def uri = resolveAddress("/success")
      runWithSpan("tracedMethod") {
        doRequest(method, uri)
      }
    }
  }

  static class SimpleHandler implements ChannelHandler {
    @Override
    void handlerAdded(ChannelHandlerContext ctx) throws Exception {
    }

    @Override
    void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
    }

    @Override
    void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
    }
  }

  static class OtherSimpleHandler implements ChannelHandler {
    @Override
    void handlerAdded(ChannelHandlerContext ctx) throws Exception {
    }

    @Override
    void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
    }

    @Override
    void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
    }
  }

  static class TracedHandlerFromInitializerHandler extends ChannelInitializer<Channel> implements ChannelHandler {
    @Override
    protected void initChannel(Channel ch) throws Exception {
      // This replicates how reactor 0.8.x add the HttpClientCodec
      ch.pipeline().addLast("added_in_initializer", new HttpClientCodec())
    }
  }

  @Override
  SingleConnection createSingleConnection(String host, int port) {
    return new SingleNettyConnection(host, port)
  }
}
