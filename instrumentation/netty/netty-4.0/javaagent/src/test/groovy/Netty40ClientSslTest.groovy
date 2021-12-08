/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

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
import io.netty.handler.ssl.SslHandler
import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestServer
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import spock.lang.Shared

import javax.net.ssl.SSLContext
import javax.net.ssl.SSLHandshakeException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

import static io.opentelemetry.api.trace.SpanKind.CLIENT
import static io.opentelemetry.api.trace.SpanKind.INTERNAL
import static io.opentelemetry.api.trace.SpanKind.SERVER
import static io.opentelemetry.api.trace.StatusCode.ERROR
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.NetTransportValues.IP_TCP

class Netty40ClientSslTest extends AgentInstrumentationSpecification {

  @Shared
  HttpClientTestServer server
  @Shared
  EventLoopGroup eventLoopGroup

  def setupSpec() {
    server = new HttpClientTestServer(openTelemetry)
    server.start()
    eventLoopGroup = new NioEventLoopGroup()
  }

  def cleanupSpec() {
    server.stop().get(10, TimeUnit.SECONDS)
    eventLoopGroup.shutdownGracefully().sync()
  }

  def "should fail SSL handshake"() {
    given:
    def bootstrap = createBootstrap(eventLoopGroup, ["SSLv3"])

    def uri = server.resolveHttpsAddress("/success")
    def request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri.path, Unpooled.EMPTY_BUFFER)
    HttpHeaders.setHost(request, uri.host + ":" + uri.port)

    when:
    Channel channel = null
    runWithSpan("parent") {
      channel = bootstrap.connect(uri.host, uri.port).sync().channel()
      def result = new CompletableFuture<Integer>()
      channel.pipeline().addLast(new ClientHandler(result))
      channel.writeAndFlush(request).get(10, TimeUnit.SECONDS)
      result.get(10, TimeUnit.SECONDS)
    }

    then:
    Throwable thrownException = thrown()
    if (thrownException instanceof ExecutionException) {
      thrownException = thrownException.cause
    }

    assertTraces(1) {
      trace(0, 3) {
        span(0) {
          name "parent"
          status ERROR
          errorEvent(thrownException.class, thrownException.message)
        }
        span(1) {
          name "CONNECT"
          kind INTERNAL
          childOf span(0)
          attributes {
            "$SemanticAttributes.NET_TRANSPORT" IP_TCP
            "$SemanticAttributes.NET_PEER_NAME" uri.host
            "$SemanticAttributes.NET_PEER_PORT" uri.port
            "$SemanticAttributes.NET_PEER_IP" { it == null || it == "127.0.0.1" }
          }
        }
        span(2) {
          name "SSL handshake"
          kind INTERNAL
          childOf span(0)
          status ERROR
          // netty swallows the exception, it doesn't make any sense to hard-code the message
          errorEventWithAnyMessage(SSLHandshakeException)
          attributes {
            "$SemanticAttributes.NET_TRANSPORT" IP_TCP
            "$SemanticAttributes.NET_PEER_NAME" uri.host
            "$SemanticAttributes.NET_PEER_PORT" uri.port
            "$SemanticAttributes.NET_PEER_IP" { it == null || it == "127.0.0.1" }
          }
        }
      }
    }

    cleanup:
    channel?.close()?.sync()
  }

  def "should successfully establish SSL handshake"() {
    given:
    def bootstrap = createBootstrap(eventLoopGroup, ["TLSv1", "TLSv1.1", "TLSv1.2", "TLSv1.3"])

    def uri = server.resolveHttpsAddress("/success")
    def request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri.path, Unpooled.EMPTY_BUFFER)
    HttpHeaders.setHost(request, uri.host + ":" + uri.port)

    when:
    Channel channel = null
    runWithSpan("parent") {
      channel = bootstrap.connect(uri.host, uri.port).sync().channel()
      def result = new CompletableFuture<Integer>()
      channel.pipeline().addLast(new ClientHandler(result))
      channel.writeAndFlush(request).get(10, TimeUnit.SECONDS)
      result.get(10, TimeUnit.SECONDS)
    }

    then:
    assertTraces(1) {
      trace(0, 5) {
        span(0) {
          name "parent"
        }
        span(1) {
          name "CONNECT"
          kind INTERNAL
          childOf span(0)
          attributes {
            "$SemanticAttributes.NET_TRANSPORT" IP_TCP
            "$SemanticAttributes.NET_PEER_NAME" uri.host
            "$SemanticAttributes.NET_PEER_PORT" uri.port
            "$SemanticAttributes.NET_PEER_IP" { it == null || it == "127.0.0.1" }
          }
        }
        span(2) {
          name "SSL handshake"
          kind INTERNAL
          childOf span(0)
          attributes {
            "$SemanticAttributes.NET_TRANSPORT" IP_TCP
            "$SemanticAttributes.NET_PEER_NAME" uri.host
            "$SemanticAttributes.NET_PEER_PORT" uri.port
            "$SemanticAttributes.NET_PEER_IP" { it == null || it == "127.0.0.1" }
          }
        }
        span(3) {
          name "HTTP GET"
          kind CLIENT
          childOf(span(0))
        }
        span(4) {
          name "test-http-server"
          kind SERVER
          childOf(span(3))
        }
      }
    }

    cleanup:
    channel?.close()?.sync()
  }

  // list of default ciphers copied from netty's JdkSslContext
  private static final String[] SUPPORTED_CIPHERS = [
    "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
    "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA",
    "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA",
    "TLS_RSA_WITH_AES_128_GCM_SHA256",
    "TLS_RSA_WITH_AES_128_CBC_SHA",
    "TLS_RSA_WITH_AES_256_CBC_SHA",
    "SSL_RSA_WITH_3DES_EDE_CBC_SHA"
  ]

  private static Bootstrap createBootstrap(EventLoopGroup eventLoopGroup, List<String> enabledProtocols) {
    def bootstrap = new Bootstrap()
    bootstrap.group(eventLoopGroup)
      .channel(NioSocketChannel)
      .handler(new ChannelInitializer<SocketChannel>() {
        @Override
        protected void initChannel(SocketChannel socketChannel) throws Exception {
          ChannelPipeline pipeline = socketChannel.pipeline()

          def sslContext = SSLContext.getInstance("TLS")
          sslContext.init(null, null, null)
          def sslEngine = sslContext.createSSLEngine()
          sslEngine.setUseClientMode(true)
          sslEngine.setEnabledProtocols(enabledProtocols as String[])
          sslEngine.setEnabledCipherSuites(SUPPORTED_CIPHERS)
          pipeline.addLast(new SslHandler(sslEngine))

          pipeline.addLast(new HttpClientCodec())
        }
      })
    bootstrap
  }
}
