/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.netty.bootstrap.ServerBootstrap
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelPipeline
import io.netty.channel.EventLoopGroup
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http.HttpHeaders
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.HttpRequestDecoder
import io.netty.handler.codec.http.HttpResponseEncoder
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.QueryStringDecoder
import io.netty.handler.logging.LogLevel
import io.netty.handler.logging.LoggingHandler
import io.netty.util.CharsetUtil
import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.base.HttpServerTest

import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_LENGTH
import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE
import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.CAPTURE_HEADERS
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.ERROR
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.EXCEPTION
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.INDEXED_CHILD
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.NOT_FOUND
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.QUERY_PARAM
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.REDIRECT
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.SUCCESS

class Netty40ServerTest extends HttpServerTest<EventLoopGroup> implements AgentTestTrait {

  static final LoggingHandler LOGGING_HANDLER = new LoggingHandler(SERVER_LOGGER.name, LogLevel.DEBUG)

  @Override
  EventLoopGroup startServer(int port) {
    def eventLoopGroup = new NioEventLoopGroup()

    ServerBootstrap bootstrap = new ServerBootstrap()
      .group(eventLoopGroup)
      .handler(LOGGING_HANDLER)
      .childHandler([
        initChannel: { ch ->
          ChannelPipeline pipeline = ch.pipeline()
          pipeline.addFirst("logger", LOGGING_HANDLER)

          def handlers = [new HttpRequestDecoder(), new HttpResponseEncoder()]
          handlers.each { pipeline.addLast(it) }
          pipeline.addLast([
            channelRead0       : { ctx, msg ->
              if (msg instanceof HttpRequest) {
                def request = msg as HttpRequest
                def uri = URI.create(request.uri)
                ServerEndpoint endpoint = ServerEndpoint.forPath(uri.path)
                ctx.write controller(endpoint) {
                  ByteBuf content = null
                  FullHttpResponse response
                  switch (endpoint) {
                    case SUCCESS:
                    case ERROR:
                      content = Unpooled.copiedBuffer(endpoint.body, CharsetUtil.UTF_8)
                      response = new DefaultFullHttpResponse(HTTP_1_1, HttpResponseStatus.valueOf(endpoint.status), content)
                      break
                    case INDEXED_CHILD:
                      content = Unpooled.EMPTY_BUFFER
                      endpoint.collectSpanAttributes { new QueryStringDecoder(uri).parameters().get(it).find() }
                      response = new DefaultFullHttpResponse(HTTP_1_1, HttpResponseStatus.valueOf(endpoint.status), content)
                      break
                    case QUERY_PARAM:
                      content = Unpooled.copiedBuffer(uri.query, CharsetUtil.UTF_8)
                      response = new DefaultFullHttpResponse(HTTP_1_1, HttpResponseStatus.valueOf(endpoint.status), content)
                      break
                    case REDIRECT:
                      content = Unpooled.EMPTY_BUFFER
                      response = new DefaultFullHttpResponse(HTTP_1_1, HttpResponseStatus.valueOf(endpoint.status), content)
                      response.headers().set(HttpHeaders.Names.LOCATION, endpoint.body)
                      break
                    case CAPTURE_HEADERS:
                      content = Unpooled.copiedBuffer(endpoint.body, CharsetUtil.UTF_8)
                      response = new DefaultFullHttpResponse(HTTP_1_1, HttpResponseStatus.valueOf(endpoint.status), content)
                      response.headers().set("X-Test-Response", request.headers().get("X-Test-Request"))
                      break
                    case EXCEPTION:
                      throw new Exception(endpoint.body)
                    default:
                      content = Unpooled.copiedBuffer(NOT_FOUND.body, CharsetUtil.UTF_8)
                      response = new DefaultFullHttpResponse(HTTP_1_1, HttpResponseStatus.valueOf(NOT_FOUND.status), content)
                      break
                  }
                  response.headers().set(CONTENT_TYPE, "text/plain")
                  if (content) {
                    response.headers().set(CONTENT_LENGTH, content.readableBytes())
                  }
                  return response
                }
              }
            },
            exceptionCaught    : { ChannelHandlerContext ctx, Throwable cause ->
              ByteBuf content = Unpooled.copiedBuffer(cause.message, CharsetUtil.UTF_8)
              FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, INTERNAL_SERVER_ERROR, content)
              response.headers().set(CONTENT_TYPE, "text/plain")
              response.headers().set(CONTENT_LENGTH, content.readableBytes())
              ctx.write(response)
            },
            channelReadComplete: { it.flush() }
          ] as SimpleChannelInboundHandler)
        }
      ] as ChannelInitializer).channel(NioServerSocketChannel)
    bootstrap.bind(port).sync()

    return eventLoopGroup
  }

  @Override
  void stopServer(EventLoopGroup server) {
    server?.shutdownGracefully()
  }

  @Override
  String expectedServerSpanName(ServerEndpoint endpoint) {
    return "HTTP GET"
  }
}
