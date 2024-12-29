/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4_0.server;

import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.CAPTURE_HEADERS;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.ERROR;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.EXCEPTION;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.INDEXED_CHILD;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.NOT_FOUND;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.QUERY_PARAM;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.REDIRECT;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.SUCCESS;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_ROUTE;

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
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.CharsetUtil;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions;
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint;
import java.net.URI;
import java.util.HashSet;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.extension.RegisterExtension;

class Netty40ServerTest extends AbstractHttpServerTest<EventLoopGroup> {

  @RegisterExtension
  static final InstrumentationExtension testing = HttpServerInstrumentationExtension.forAgent();

  private static final LoggingHandler LOGGING_HANDLER =
      new LoggingHandler(Netty40ServerTest.class, LogLevel.DEBUG);

  @Override
  protected EventLoopGroup setupServer() throws InterruptedException {
    EventLoopGroup eventLoopGroup = new NioEventLoopGroup();

    ServerBootstrap serverBootstrap =
        new ServerBootstrap()
            .group(eventLoopGroup)
            .handler(LOGGING_HANDLER)
            .childHandler(
                new ChannelInitializer<SocketChannel>() {
                  @Override
                  protected void initChannel(@NotNull SocketChannel socketChannel)
                      throws Exception {
                    ChannelPipeline pipeline = socketChannel.pipeline();
                    pipeline.addFirst("logger", LOGGING_HANDLER);
                    pipeline.addLast(new HttpRequestDecoder());
                    pipeline.addLast(new HttpResponseEncoder());
                    pipeline.addLast(
                        new SimpleChannelInboundHandler<Object>() {

                          @Override
                          protected void channelRead0(ChannelHandlerContext ctx, Object msg)
                              throws Exception {
                            if (!(msg instanceof HttpRequest)) {
                              return;
                            }
                            HttpRequest request = (HttpRequest) msg;
                            URI uri = URI.create(request.getUri());
                            ServerEndpoint endpoint = ServerEndpoint.forPath(uri.getPath());
                            ctx.write(
                                controller(
                                    endpoint,
                                    () -> {
                                      ByteBuf content;
                                      FullHttpResponse response;
                                      if (endpoint.equals(SUCCESS) || endpoint.equals(ERROR)) {
                                        content =
                                            Unpooled.copiedBuffer(
                                                endpoint.getBody(), CharsetUtil.UTF_8);
                                        response =
                                            new DefaultFullHttpResponse(
                                                HTTP_1_1,
                                                HttpResponseStatus.valueOf(endpoint.getStatus()),
                                                content);
                                      } else if (endpoint.equals(INDEXED_CHILD)) {
                                        content = Unpooled.EMPTY_BUFFER;
                                        endpoint.collectSpanAttributes(
                                            it ->
                                                new QueryStringDecoder(uri)
                                                    .parameters()
                                                    .get(it)
                                                    .get(0));
                                        response =
                                            new DefaultFullHttpResponse(
                                                HTTP_1_1,
                                                HttpResponseStatus.valueOf(endpoint.getStatus()),
                                                content);
                                      } else if (endpoint.equals(QUERY_PARAM)) {
                                        content =
                                            Unpooled.copiedBuffer(
                                                uri.getQuery(), CharsetUtil.UTF_8);
                                        response =
                                            new DefaultFullHttpResponse(
                                                HTTP_1_1,
                                                HttpResponseStatus.valueOf(endpoint.getStatus()),
                                                content);
                                      } else if (endpoint.equals(REDIRECT)) {
                                        content = Unpooled.EMPTY_BUFFER;
                                        response =
                                            new DefaultFullHttpResponse(
                                                HTTP_1_1,
                                                HttpResponseStatus.valueOf(endpoint.getStatus()),
                                                content);
                                        response
                                            .headers()
                                            .set(HttpHeaders.Names.LOCATION, endpoint.getBody());
                                      } else if (endpoint.equals(CAPTURE_HEADERS)) {
                                        content =
                                            Unpooled.copiedBuffer(
                                                endpoint.getBody(), CharsetUtil.UTF_8);
                                        response =
                                            new DefaultFullHttpResponse(
                                                HTTP_1_1,
                                                HttpResponseStatus.valueOf(endpoint.getStatus()),
                                                content);
                                        response
                                            .headers()
                                            .set(
                                                "X-Test-Response",
                                                request.headers().get("X-Test-Request"));
                                      } else if (endpoint.equals(EXCEPTION)) {
                                        throw new IllegalArgumentException(endpoint.getBody());
                                      } else {
                                        content =
                                            Unpooled.copiedBuffer(
                                                NOT_FOUND.getBody(), CharsetUtil.UTF_8);
                                        response =
                                            new DefaultFullHttpResponse(
                                                HTTP_1_1,
                                                HttpResponseStatus.valueOf(NOT_FOUND.getStatus()),
                                                content);
                                      }

                                      response.headers().set(CONTENT_TYPE, "text/plain");
                                      response
                                          .headers()
                                          .set(CONTENT_LENGTH, content.readableBytes());
                                      return response;
                                    }));
                          }

                          @Override
                          public void exceptionCaught(ChannelHandlerContext ctx, Throwable ex)
                              throws Exception {
                            ByteBuf content =
                                Unpooled.copiedBuffer(ex.getMessage(), CharsetUtil.UTF_8);
                            FullHttpResponse response =
                                new DefaultFullHttpResponse(
                                    HTTP_1_1, INTERNAL_SERVER_ERROR, content);
                            response.headers().set(CONTENT_TYPE, "text/plain");
                            response.headers().set(CONTENT_LENGTH, content.readableBytes());
                            ctx.write(response);
                          }

                          @Override
                          public void channelReadComplete(ChannelHandlerContext ctx)
                              throws Exception {
                            ctx.flush();
                          }
                        });
                  }
                })
            .channel(NioServerSocketChannel.class);
    serverBootstrap.bind(port).sync();

    return eventLoopGroup;
  }

  @Override
  protected void stopServer(EventLoopGroup server) {
    if (server != null) {
      server.shutdownGracefully();
    }
  }

  @Override
  protected void configure(HttpServerTestOptions options) {
    options.setHttpAttributes(
        serverEndpoint -> {
          Set<AttributeKey<?>> attributes =
              new HashSet<>(HttpServerTestOptions.DEFAULT_HTTP_ATTRIBUTES);
          attributes.remove(HTTP_ROUTE);
          return attributes;
        });

    options.setExpectedException(new IllegalArgumentException(EXCEPTION.getBody()));
    options.setHasResponseCustomizer(serverEndpoint -> true);
  }
}
