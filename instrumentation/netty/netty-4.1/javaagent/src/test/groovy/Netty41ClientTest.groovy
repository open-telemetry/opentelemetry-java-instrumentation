/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static io.opentelemetry.instrumentation.test.utils.PortUtils.UNUSABLE_PORT
import static io.opentelemetry.instrumentation.test.utils.TraceUtils.basicSpan
import static io.opentelemetry.instrumentation.test.utils.TraceUtils.runUnderTrace
import static org.junit.Assume.assumeTrue

import io.netty.bootstrap.Bootstrap
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInitializer
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
import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.base.HttpClientTest
import io.opentelemetry.javaagent.instrumentation.netty.v4_1.client.HttpClientTracingHandler
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import spock.lang.Shared
import spock.lang.Timeout

@Timeout(5)
class Netty41ClientTest extends HttpClientTest implements AgentTestTrait {

  @Shared
  private Bootstrap bootstrap

  def setupSpec() {
    EventLoopGroup group = new NioEventLoopGroup()
    bootstrap = new Bootstrap()
    bootstrap.group(group)
      .channel(NioSocketChannel)
      .handler(new ChannelInitializer<SocketChannel>() {
        @Override
        protected void initChannel(SocketChannel socketChannel) throws Exception {
          ChannelPipeline pipeline = socketChannel.pipeline()
          pipeline.addLast(new HttpClientCodec())
        }
      })

  }

  @Override
  int doRequest(String method, URI uri, Map<String, String> headers, Closure callback) {
    Channel ch = bootstrap.connect(uri.host, uri.port).sync().channel()
    def result = new CompletableFuture<Integer>()
    ch.pipeline().addLast(new ClientHandler(callback, result))

    def request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.valueOf(method), uri.toString(), Unpooled.EMPTY_BUFFER)
    request.headers().set(HttpHeaderNames.HOST, uri.host)
    headers.each { k, v -> request.headers().set(k, v) }

    ch.writeAndFlush(request).get()
    return result.get(20, TimeUnit.SECONDS)
  }

  @Override
  boolean testRedirects() {
    false
  }

  @Override
  boolean testConnectionFailure() {
    false
  }

  @Override
  boolean testRemoteConnection() {
    return false
  }

  def "test connection interference"() {
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

    //Important! Separate connect, outside of any request
    Channel ch = b.connect(server.address.host, server.address.port).sync().channel()

    def request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, server.address.resolve("/success").toString(), Unpooled.EMPTY_BUFFER)
    request.headers().set(HttpHeaderNames.HOST, server.address.host)

    when:
    runUnderTrace("parent1") {
      ch.writeAndFlush(request).get()
    }
    runUnderTrace("parent2") {
      ch.writeAndFlush(request).get()
    }

    then:
    assertTraces(2) {
      trace(0, 3) {
        basicSpan(it, 0, "parent1")
        clientSpan(it, 1, span(0))
        serverSpan(it, 2, span(1))
      }
      trace(1, 3) {
        basicSpan(it, 0, "parent2")
        clientSpan(it, 1, span(0))
        serverSpan(it, 2, span(1))
      }
    }
    cleanup:
    group.shutdownGracefully()
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
    def request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, server.address.resolve("/success").toString(), Unpooled.EMPTY_BUFFER)
    request.headers().set(HttpHeaderNames.HOST, server.address.host)
    Channel ch = null

    when:
    // note that this is a purely asynchronous request
    runUnderTrace("parent1") {
      ch = b.connect(server.address.host, server.address.port).sync().channel()
      ch.write(request)
      ch.flush()
    }
    // This is a cheap/easy way to block/ensure that the first request has finished and check reported spans midway through
    // the complex sequence of events
    assertTraces(1) {
      trace(0, 3) {
        basicSpan(it, 0, "parent1")
        clientSpan(it, 1, span(0))
        serverSpan(it, 2, span(1))
      }
    }

    then:
    // now run a second request through the same channel
    runUnderTrace("parent2") {
      ch.write(request)
      ch.flush()
    }

    assertTraces(2) {
      trace(0, 3) {
        basicSpan(it, 0, "parent1")
        clientSpan(it, 1, span(0))
        serverSpan(it, 2, span(1))
      }
      trace(1, 3) {
        basicSpan(it, 0, "parent2")
        clientSpan(it, 1, span(0))
        serverSpan(it, 2, span(1))
      }
    }


    cleanup:
    group.shutdownGracefully()
  }

  def "connection error (unopened port)"() {
    given:
    def uri = new URI("http://localhost:$UNUSABLE_PORT/")

    when:
    runUnderTrace("parent") {
      doRequest(method, uri)
    }

    then:
    def ex = thrown(Exception)
    def thrownException = ex instanceof ExecutionException ? ex.cause : ex
    thrownException instanceof ConnectException || thrownException instanceof TimeoutException

    and:
    assertTraces(1) {
      def size = traces[0].size()
      trace(0, size) {
        basicSpan(it, 0, "parent", null, thrownException)

        // AsyncHttpClient retries across multiple resolved IP addresses (e.g. 127.0.0.1 and 0:0:0:0:0:0:0:1)
        // for up to a total of 10 seconds (default connection time limit)
        for (def i = 1; i < size; i++) {
          span(i) {
            name "CONNECT"
            childOf span(0)
            errored true
            errorEvent(thrownException.class, ~/Connection refused:( no further information:)? localhost\/\[?[0-9.:]+\]?:$UNUSABLE_PORT/)
          }
        }
      }
    }

    where:
    method = "GET"
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
    null != channel.pipeline().remove("added_in_initializer")
    null != channel.pipeline().remove(HttpClientTracingHandler.getName())
  }

  def "request with trace annotated method #method"() {
    given:
    def annotatedClass = new TracedClass()

    when:
    def status = runUnderTrace("parent") {
      annotatedClass.tracedMethod(method)
    }

    then:
    status == 200
    assertTraces(1) {
      trace(0, 4) {
        basicSpan(it, 0, "parent")
        span(1) {
          childOf span(0)
          name "tracedMethod"
          errored false
          attributes {
          }
        }
        clientSpan(it, 2, span(1), method)
        server.distributedRequestSpan(it, 3, span(2))
      }
    }

    where:
    method << BODY_METHODS
  }

  class TracedClass {
    int tracedMethod(String method) {
      runUnderTrace("tracedMethod") {
        doRequest(method, server.address.resolve("/success"))
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
}
