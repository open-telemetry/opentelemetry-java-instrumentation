/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */


import io.netty.bootstrap.Bootstrap
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelPipeline
import io.netty.channel.EventLoopGroup
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.http.DefaultFullHttpRequest
import io.netty.handler.codec.http.HttpClientCodec
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpVersion
import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.InstrumentationSpecification
import io.opentelemetry.instrumentation.test.utils.PortUtils
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestServer
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import spock.lang.Shared

import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

import static io.opentelemetry.api.trace.SpanKind.CLIENT
import static io.opentelemetry.api.trace.SpanKind.INTERNAL
import static io.opentelemetry.api.trace.SpanKind.SERVER
import static io.opentelemetry.api.trace.StatusCode.ERROR
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.NetTransportValues.IP_TCP

class Netty41ConnectionSpanTest extends InstrumentationSpecification implements AgentTestTrait {

  @Shared
  private HttpClientTestServer server

  @Shared
  private EventLoopGroup eventLoopGroup = new NioEventLoopGroup()

  @Shared
  private Bootstrap bootstrap = buildBootstrap()

  def setupSpec() {
    server = new HttpClientTestServer(openTelemetry)
    server.start()
  }

  def cleanupSpec() {
    eventLoopGroup.shutdownGracefully()
    server.stop()
  }

  Bootstrap buildBootstrap() {
    Bootstrap bootstrap = new Bootstrap()
    bootstrap.group(eventLoopGroup)
      .channel(NioSocketChannel)
      .handler(new ChannelInitializer<SocketChannel>() {
        @Override
        protected void initChannel(SocketChannel socketChannel) throws Exception {
          ChannelPipeline pipeline = socketChannel.pipeline()
          pipeline.addLast(new HttpClientCodec())
        }
      })

    return bootstrap
  }

  DefaultFullHttpRequest buildRequest(String method, URI uri, Map<String, String> headers) {
    def request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.valueOf(method), uri.toString(), Unpooled.EMPTY_BUFFER)
    request.headers().set(HttpHeaderNames.HOST, uri.host)
    headers.each { k, v -> request.headers().set(k, v) }
    return request
  }

  int sendRequest(DefaultFullHttpRequest request, URI uri) {
    def channel = bootstrap.connect(uri.host, uri.port).sync().channel()
    def result = new CompletableFuture<Integer>()
    channel.pipeline().addLast(new ClientHandler(result))
    channel.writeAndFlush(request).get()
    return result.get(20, TimeUnit.SECONDS)
  }

  def "test successful request"() {
    when:
    def uri = URI.create("http://localhost:${server.httpPort()}/success")
    def request = buildRequest("GET", uri, [:])
    def responseCode = runWithSpan("parent") {
      sendRequest(request, uri)
    }

    then:
    responseCode == 200
    assertTraces(1) {
      trace(0, 4) {
        span(0) {
          name "parent"
          kind INTERNAL
          hasNoParent()
        }
        span(1) {
          name "CONNECT"
          kind INTERNAL
          childOf(span(0))
          attributes {
            "${SemanticAttributes.NET_TRANSPORT.key}" IP_TCP
            "${SemanticAttributes.NET_PEER_NAME.key}" uri.host
            "${SemanticAttributes.NET_PEER_PORT.key}" uri.port
            "${SemanticAttributes.NET_PEER_IP.key}" "127.0.0.1"
          }
        }
        span(2) {
          name "HTTP GET"
          kind CLIENT
          childOf(span(0))
        }
        span(3) {
          name "test-http-server"
          kind SERVER
          childOf(span(2))
        }
      }
    }
  }

  def "test failing request"() {
    when:
    URI uri = URI.create("http://localhost:${PortUtils.UNUSABLE_PORT}")
    def request = buildRequest("GET", uri, [:])
    runWithSpan("parent") {
      sendRequest(request, uri)
    }

    then:
    def thrownException = thrown(Exception)

    and:
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          name "parent"
          kind INTERNAL
          hasNoParent()
          status ERROR
          errorEvent(thrownException.class, thrownException.message)
        }
        span(1) {
          name "CONNECT"
          kind INTERNAL
          childOf(span(0))
          status ERROR
          errorEvent(thrownException.class, thrownException.message)
          attributes {
            "${SemanticAttributes.NET_TRANSPORT.key}" IP_TCP
            "${SemanticAttributes.NET_PEER_NAME.key}" uri.host
            "${SemanticAttributes.NET_PEER_PORT.key}" uri.port
            "${SemanticAttributes.NET_PEER_IP.key}" { it == null || it == "127.0.0.1" }
          }
        }
      }
    }
  }
}
