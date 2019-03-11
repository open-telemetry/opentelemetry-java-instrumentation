import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.agent.test.utils.OkHttpUtils
import datadog.trace.agent.test.utils.PortUtils
import datadog.trace.api.DDSpanTypes
import io.netty.bootstrap.ServerBootstrap
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelPipeline
import io.netty.channel.EventLoopGroup
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http.HttpRequestDecoder
import io.netty.handler.codec.http.HttpResponseEncoder
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpServerCodec
import io.netty.handler.codec.http.HttpVersion
import io.netty.handler.codec.http.LastHttpContent
import io.netty.handler.logging.LogLevel
import io.netty.handler.logging.LoggingHandler
import io.netty.util.CharsetUtil
import io.opentracing.tag.Tags
import okhttp3.OkHttpClient
import okhttp3.Request
import spock.lang.Shared

import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_LENGTH
import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE

class Netty40ServerTest extends AgentTestRunner {

  @Shared
  OkHttpClient client = OkHttpUtils.client()

  def "test server request/response"() {
    setup:
    EventLoopGroup eventLoopGroup = new NioEventLoopGroup()
    int port = PortUtils.randomOpenPort()
    initializeServer(eventLoopGroup, port, handlers, HttpResponseStatus.OK)

    def request = new Request.Builder()
      .url("http://localhost:$port/")
      .header("x-datadog-trace-id", "123")
      .header("x-datadog-parent-id", "456")
      .get()
      .build()
    def response = client.newCall(request).execute()

    expect:
    response.code() == 200
    response.body().string() == "Hello World"

    and:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          traceId "123"
          parentId "456"
          serviceName "unnamed-java-app"
          operationName "netty.request"
          resourceName "GET /"
          spanType DDSpanTypes.HTTP_SERVER
          errored false
          tags {
            "$Tags.COMPONENT.key" "netty"
            "$Tags.HTTP_METHOD.key" "GET"
            "$Tags.HTTP_STATUS.key" 200
            "$Tags.HTTP_URL.key" "http://localhost:$port/"
            "$Tags.PEER_HOSTNAME.key" "localhost"
            "$Tags.PEER_HOST_IPV4.key" "127.0.0.1"
            "$Tags.PEER_PORT.key" Integer
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_SERVER
            defaultTags(true)
          }
        }
      }
    }

    cleanup:
    eventLoopGroup.shutdownGracefully()

    where:
    handlers                                              | _
    [new HttpServerCodec()]                               | _
    [new HttpRequestDecoder(), new HttpResponseEncoder()] | _
  }

  def "test #responseCode response handling"() {
    setup:
    EventLoopGroup eventLoopGroup = new NioEventLoopGroup()
    int port = PortUtils.randomOpenPort()
    initializeServer(eventLoopGroup, port, new HttpServerCodec(), responseCode)

    def request = new Request.Builder().url("http://localhost:$port/").get().build()
    def response = client.newCall(request).execute()

    expect:
    response.code() == responseCode.code()
    response.body().string() == "Hello World"

    and:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          serviceName "unnamed-java-app"
          operationName "netty.request"
          resourceName name
          spanType DDSpanTypes.HTTP_SERVER
          errored error
          tags {
            "$Tags.COMPONENT.key" "netty"
            "$Tags.HTTP_METHOD.key" "GET"
            "$Tags.HTTP_STATUS.key" responseCode.code()
            "$Tags.HTTP_URL.key" "http://localhost:$port/"
            "$Tags.PEER_HOSTNAME.key" "localhost"
            "$Tags.PEER_HOST_IPV4.key" "127.0.0.1"
            "$Tags.PEER_PORT.key" Integer
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_SERVER
            if (error) {
              tag("error", true)
            }
            defaultTags()
          }
        }
      }
    }

    cleanup:
    eventLoopGroup.shutdownGracefully()

    where:
    responseCode                             | name    | error
    HttpResponseStatus.OK                    | "GET /" | false
    HttpResponseStatus.NOT_FOUND             | "404"   | false
    HttpResponseStatus.INTERNAL_SERVER_ERROR | "GET /" | true
  }

  def initializeServer(eventLoopGroup, port, handlers, responseCode) {
    ServerBootstrap bootstrap = new ServerBootstrap()
      .group(eventLoopGroup)
      .handler(new LoggingHandler(LogLevel.INFO))
      .childHandler([
      initChannel: { ch ->
        ChannelPipeline pipeline = ch.pipeline()
        handlers.each { pipeline.addLast(it) }
        pipeline.addLast([
          channelRead0       : { ctx, msg ->
            if (msg instanceof LastHttpContent) {
              ByteBuf content = Unpooled.copiedBuffer("Hello World", CharsetUtil.UTF_8)
              FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, responseCode, content)
              response.headers().set(CONTENT_TYPE, "text/plain")
              response.headers().set(CONTENT_LENGTH, content.readableBytes())
              ctx.write(response)
            }
          },
          channelReadComplete: { it.flush() }
        ] as SimpleChannelInboundHandler)
      }
    ] as ChannelInitializer).channel(NioServerSocketChannel)
    bootstrap.bind(port).sync()
  }
}
