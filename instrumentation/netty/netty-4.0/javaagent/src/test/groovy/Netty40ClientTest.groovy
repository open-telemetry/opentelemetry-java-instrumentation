/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static io.opentelemetry.instrumentation.test.utils.PortUtils.UNUSABLE_PORT
import static io.opentelemetry.instrumentation.test.utils.TraceUtils.basicSpan
import static io.opentelemetry.instrumentation.test.utils.TraceUtils.runUnderTrace

import io.netty.bootstrap.Bootstrap
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelPipeline
import io.netty.channel.EventLoopGroup
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.http.DefaultFullHttpRequest
import io.netty.handler.codec.http.HttpClientCodec
import io.netty.handler.codec.http.HttpHeaders
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpVersion
import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.base.HttpClientTest
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import spock.lang.Shared

class Netty40ClientTest extends HttpClientTest implements AgentTestTrait {

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
    HttpHeaders.setHost(request, uri.host)
    request.headers().set("user-agent", userAgent())
    headers.each { k, v -> request.headers().set(k, v) }

    ch.writeAndFlush(request).get()
    return result.get(20, TimeUnit.SECONDS)
  }

  @Override
  String userAgent() {
    return "Netty"
  }

  @Override
  boolean testRedirects() {
    false
  }

  @Override
  boolean testRemoteConnection() {
    return false
  }

  @Override
  boolean testConnectionFailure() {
    false
  }

  //This is almost identical to "connection error (unopened port)" test from superclass.
  //But it uses somewhat different span name for the client span.
  //For now creating a separate test for this, hoping to remove this duplication in the future.
  def "netty connection error (unopened port)"() {
    given:
    def uri = new URI("http://127.0.0.1:$UNUSABLE_PORT/") // Use numeric address to avoid ipv4/ipv6 confusion

    when:
    runUnderTrace("parent") {
      doRequest(method, uri)
    }

    then:
    def ex = thrown(Exception)
    def thrownException = ex instanceof ExecutionException ? ex.cause : ex

    and:
    assertTraces(1) {
      trace(0, 2) {
        basicSpan(it, 0, "parent", null, thrownException)
        span(1) {
          name "CONNECT"
          childOf span(0)
          errored true
          Class errorClass = ConnectException
          try {
            errorClass = Class.forName('io.netty.channel.AbstractChannel$AnnotatedConnectException')
          } catch (ClassNotFoundException e) {
            // Older versions use 'java.net.ConnectException' and do not have 'io.netty.channel.AbstractChannel$AnnotatedConnectException'
          }
          errorEvent(errorClass, "Connection refused: /127.0.0.1:$UNUSABLE_PORT")
        }

      }
    }

    where:
    method = "GET"
  }
}
