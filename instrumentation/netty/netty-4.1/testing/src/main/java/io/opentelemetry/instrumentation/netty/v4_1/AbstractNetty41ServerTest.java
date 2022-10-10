/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.netty.v4_1;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.NOT_FOUND;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.CharsetUtil;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions;
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint;
import java.net.URI;
import java.util.function.Supplier;

public abstract class AbstractNetty41ServerTest extends AbstractHttpServerTest<EventLoopGroup> {

  static final LoggingHandler LOGGING_HANDLER =
      new LoggingHandler(AbstractNetty41ServerTest.class, LogLevel.DEBUG);

  protected abstract void configurePipeline(ChannelPipeline channelPipeline);

  @Override
  protected void configure(HttpServerTestOptions options) {
    options.setTestException(false);
    // etty instrumentation collects route but doesn't use it for span names
    options.setExpectedHttpRoute(unused -> null);
    options.setExpectedServerSpanNameMapper((endpoint, method) -> "HTTP " + method);
  }

  @Override
  protected EventLoopGroup setupServer() {
    NioEventLoopGroup eventLoopGroup = new NioEventLoopGroup();

    ServerBootstrap bootstrap =
        new ServerBootstrap()
            .group(eventLoopGroup)
            .handler(LOGGING_HANDLER)
            .childHandler(
                new ChannelInitializer<SocketChannel>() {
                  @Override
                  protected void initChannel(SocketChannel socketChannel) {
                    ChannelPipeline pipeline = socketChannel.pipeline();
                    pipeline.addFirst("logger", LOGGING_HANDLER);
                    pipeline.addLast(new HttpServerCodec());
                    pipeline.addLast(
                        new SimpleChannelInboundHandler<Object>() {
                          @Override
                          protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
                            if (msg instanceof HttpRequest) {
                              HttpRequest request = (HttpRequest) msg;
                              URI uri = URI.create(request.uri());
                              ServerEndpoint endpoint = ServerEndpoint.forPath(uri.getPath());
                              ctx.write(
                                  controller(
                                      endpoint,
                                      new Supplier<Object>() {
                                        @Override
                                        public Object get() {
                                          ByteBuf content;
                                          FullHttpResponse response;
                                          switch (endpoint) {
                                            case SUCCESS:
                                            case ERROR:
                                              content =
                                                  Unpooled.copiedBuffer(
                                                      endpoint.getBody(), CharsetUtil.UTF_8);
                                              response =
                                                  new DefaultFullHttpResponse(
                                                      HTTP_1_1,
                                                      HttpResponseStatus.valueOf(
                                                          endpoint.getStatus()),
                                                      content);
                                              break;
                                            case INDEXED_CHILD:
                                              content = Unpooled.EMPTY_BUFFER;
                                              endpoint.collectSpanAttributes(
                                                  name ->
                                                      new QueryStringDecoder(uri)
                                                          .parameters().get(name).stream()
                                                              .findFirst()
                                                              .orElse(""));
                                              response =
                                                  new DefaultFullHttpResponse(
                                                      HTTP_1_1,
                                                      HttpResponseStatus.valueOf(
                                                          endpoint.getStatus()),
                                                      content);
                                              break;
                                            case QUERY_PARAM:
                                              content =
                                                  Unpooled.copiedBuffer(
                                                      uri.getQuery(), CharsetUtil.UTF_8);
                                              response =
                                                  new DefaultFullHttpResponse(
                                                      HTTP_1_1,
                                                      HttpResponseStatus.valueOf(
                                                          endpoint.getStatus()),
                                                      content);
                                              break;
                                            case REDIRECT:
                                              content = Unpooled.EMPTY_BUFFER;
                                              response =
                                                  new DefaultFullHttpResponse(
                                                      HTTP_1_1,
                                                      HttpResponseStatus.valueOf(
                                                          endpoint.getStatus()),
                                                      content);
                                              response
                                                  .headers()
                                                  .set(
                                                      HttpHeaderNames.LOCATION, endpoint.getBody());
                                              break;
                                            case CAPTURE_HEADERS:
                                              content =
                                                  Unpooled.copiedBuffer(
                                                      endpoint.getBody(), CharsetUtil.UTF_8);
                                              response =
                                                  new DefaultFullHttpResponse(
                                                      HTTP_1_1,
                                                      HttpResponseStatus.valueOf(
                                                          endpoint.getStatus()),
                                                      content);
                                              response
                                                  .headers()
                                                  .set(
                                                      "X-Test-Response",
                                                      request.headers().get("X-Test-Request"));
                                              break;
                                            case EXCEPTION:
                                              throw new IllegalStateException(endpoint.getBody());
                                            default:
                                              content =
                                                  Unpooled.copiedBuffer(
                                                      NOT_FOUND.getBody(), CharsetUtil.UTF_8);
                                              response =
                                                  new DefaultFullHttpResponse(
                                                      HTTP_1_1,
                                                      HttpResponseStatus.valueOf(
                                                          NOT_FOUND.getStatus()),
                                                      content);
                                              break;
                                          }
                                          response.headers().set(CONTENT_TYPE, "text/plain");
                                          response
                                              .headers()
                                              .set(CONTENT_LENGTH, content.readableBytes());
                                          return response;
                                        }
                                      }));
                            }
                          }

                          @Override
                          public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                            ByteBuf content =
                                Unpooled.copiedBuffer(cause.getMessage(), CharsetUtil.UTF_8);
                            FullHttpResponse response =
                                new DefaultFullHttpResponse(
                                    HTTP_1_1, INTERNAL_SERVER_ERROR, content);
                            response.headers().set(CONTENT_TYPE, "text/plain");
                            response.headers().set(CONTENT_LENGTH, content.readableBytes());
                            ctx.write(response);
                          }

                          @Override
                          public void channelReadComplete(ChannelHandlerContext ctx) {
                            ctx.flush();
                          }
                        });
                    configurePipeline(pipeline);
                  }
                })
            .channel(NioServerSocketChannel.class);
    try {
      bootstrap.bind(port).sync();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }

    return eventLoopGroup;
  }

  @Override
  protected void stopServer(EventLoopGroup server) {
    server.shutdownGracefully();
  }
}
