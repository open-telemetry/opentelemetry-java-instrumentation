import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.agent.test.TestUtils
import datadog.trace.api.DDSpanTypes
import datadog.trace.api.DDTags
import io.netty.bootstrap.ServerBootstrap
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelInitializer
import io.netty.channel.EventLoopGroup
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http.HttpHeaderNames
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

import static datadog.trace.agent.test.ListWriterAssert.assertTraces

class NettyServerTest extends AgentTestRunner {
  static {
    System.setProperty("dd.integration.netty.enabled", "true")
  }

  static final PORT = TestUtils.randomOpenPort()

  @Shared
  EventLoopGroup eventLoopGroup = new NioEventLoopGroup()

  OkHttpClient client = new OkHttpClient.Builder()
  // Uncomment when debugging:
//    .connectTimeout(1, TimeUnit.HOURS)
//    .writeTimeout(1, TimeUnit.HOURS)
//    .readTimeout(1, TimeUnit.HOURS)
    .build()

  def setupSpec() {
    ServerBootstrap bootstrap = new ServerBootstrap()
      .group(eventLoopGroup)
      .handler(new LoggingHandler(LogLevel.INFO))
      .childHandler([
      initChannel: { ch ->
        def pipeline = ch.pipeline()
        pipeline.addLast(new HttpServerCodec())
        pipeline.addLast([
          channelRead0       : { ctx, msg ->
            if (msg instanceof LastHttpContent) {
              ByteBuf content = Unpooled.copiedBuffer("Hello World", CharsetUtil.UTF_8)
              FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, content)
              response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain")
              response.headers().set(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes())
              ctx.write(response)
            }
          },
          channelReadComplete: { it.flush() }
        ] as SimpleChannelInboundHandler)
      }
    ] as ChannelInitializer)

      .channel(NioServerSocketChannel)
    bootstrap.bind(PORT).sync()
  }

  def cleanupSpec() {
    eventLoopGroup.shutdownGracefully()
  }

  def "test server request/response"() {
    setup:
    def request = new Request.Builder().url("http://localhost:$PORT/").get().build()
    def response = client.newCall(request).execute()

    expect:
    response.code() == 200
    response.body().string() == "Hello World"

    and:
    assertTraces(TEST_WRITER, 1) {
      trace(0, 1) {
        span(0) {
          serviceName "unnamed-java-app"
          operationName "netty.request"
          resourceName "GET /"
          spanType DDSpanTypes.WEB_SERVLET
          errored false
          tags {
            "$Tags.COMPONENT.key" "netty"
            "$Tags.HTTP_METHOD.key" "GET"
            "$Tags.HTTP_STATUS.key" 200
            "$Tags.HTTP_URL.key" "http://localhost:$PORT/"
            "$Tags.PEER_HOSTNAME.key" "localhost"
            "$Tags.PEER_PORT.key" Integer
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_SERVER
            "$DDTags.SPAN_TYPE" DDSpanTypes.WEB_SERVLET
            defaultTags()
          }
        }
      }
    }
  }
}
