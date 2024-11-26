/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.netty.v4_1;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions.DEFAULT_HTTP_ATTRIBUTES;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.CAPTURE_HEADERS;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.ERROR;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.EXCEPTION;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.INDEXED_CHILD;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.NOT_FOUND;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.QUERY_PARAM;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.REDIRECT;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.SUCCESS;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_ROUTE;

import com.google.common.collect.Sets;
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
import java.util.Collections;

public abstract class AbstractNetty41ServerTest extends AbstractHttpServerTest<EventLoopGroup> {

  static final LoggingHandler LOGGING_HANDLER =
      new LoggingHandler(AbstractNetty41ServerTest.class, LogLevel.DEBUG);

  protected abstract void configurePipeline(ChannelPipeline channelPipeline);

  @Override
  protected void configure(HttpServerTestOptions options) {
    options.setTestException(false);
    options.setHttpAttributes(
        unused -> Sets.difference(DEFAULT_HTTP_ATTRIBUTES, Collections.singleton(HTTP_ROUTE)));
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
                    pipeline.addLast(new HttpHandler());
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

  private static class HttpHandler extends SimpleChannelInboundHandler<Object> {

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
      if (msg instanceof HttpRequest) {
        HttpRequest request = (HttpRequest) msg;
        URI uri = URI.create(request.uri());
        ServerEndpoint endpoint = ServerEndpoint.forPath(uri.getPath());
        ctx.write(controller(endpoint, () -> handle(request, uri, endpoint)));
      }
    }

    private static Object handle(HttpRequest request, URI uri, ServerEndpoint endpoint) {
      ByteBuf content;
      FullHttpResponse response;
      if (SUCCESS.equals(endpoint) || ERROR.equals(endpoint)) {
        content = Unpooled.copiedBuffer(endpoint.getBody(), CharsetUtil.UTF_8);
        response =
            new DefaultFullHttpResponse(
                HTTP_1_1, HttpResponseStatus.valueOf(endpoint.getStatus()), content);
      } else if (INDEXED_CHILD.equals(endpoint)) {
        content = Unpooled.EMPTY_BUFFER;
        endpoint.collectSpanAttributes(
            name ->
                new QueryStringDecoder(uri).parameters().get(name).stream().findFirst().orElse(""));
        response =
            new DefaultFullHttpResponse(
                HTTP_1_1, HttpResponseStatus.valueOf(endpoint.getStatus()), content);
      } else if (QUERY_PARAM.equals(endpoint)) {
        content = Unpooled.copiedBuffer(uri.getQuery(), CharsetUtil.UTF_8);
        response =
            new DefaultFullHttpResponse(
                HTTP_1_1, HttpResponseStatus.valueOf(endpoint.getStatus()), content);
      } else if (REDIRECT.equals(endpoint)) {
        content = Unpooled.EMPTY_BUFFER;
        response =
            new DefaultFullHttpResponse(
                HTTP_1_1, HttpResponseStatus.valueOf(endpoint.getStatus()), content);
        response.headers().set(HttpHeaderNames.LOCATION, endpoint.getBody());
      } else if (CAPTURE_HEADERS.equals(endpoint)) {
        content = Unpooled.copiedBuffer(endpoint.getBody(), CharsetUtil.UTF_8);
        response =
            new DefaultFullHttpResponse(
                HTTP_1_1, HttpResponseStatus.valueOf(endpoint.getStatus()), content);
        response.headers().set("X-Test-Response", request.headers().get("X-Test-Request"));
      } else if (EXCEPTION.equals(endpoint)) {
        throw new IllegalStateException(endpoint.getBody());
      } else {
        content = Unpooled.copiedBuffer(NOT_FOUND.getBody(), CharsetUtil.UTF_8);
        response =
            new DefaultFullHttpResponse(
                HTTP_1_1, HttpResponseStatus.valueOf(NOT_FOUND.getStatus()), content);
      }
      response.headers().set(CONTENT_TYPE, "text/plain");
      response.headers().set(CONTENT_LENGTH, content.readableBytes());
      return response;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
      ByteBuf content = Unpooled.copiedBuffer(cause.getMessage(), CharsetUtil.UTF_8);
      FullHttpResponse response =
          new DefaultFullHttpResponse(HTTP_1_1, INTERNAL_SERVER_ERROR, content);
      response.headers().set(CONTENT_TYPE, "text/plain");
      response.headers().set(CONTENT_LENGTH, content.readableBytes());
      ctx.write(response);
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
      ctx.flush();
    }
  }
}
