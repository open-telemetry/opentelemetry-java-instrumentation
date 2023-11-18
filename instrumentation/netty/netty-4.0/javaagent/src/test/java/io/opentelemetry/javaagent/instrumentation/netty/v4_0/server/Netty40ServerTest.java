/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4_0.server;

import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.*;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.logging.LoggingHandler;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions;
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint;
import io.opentelemetry.semconv.SemanticAttributes;
import java.net.URI;
import java.util.HashSet;
import java.util.Set;
import org.apache.rocketmq.shaded.io.grpc.netty.shaded.io.netty.util.CharsetUtil;
import org.apache.rocketmq.shaded.io.grpc.netty.shaded.io.netty.util.internal.logging.InternalLoggerFactory;
import org.apache.rocketmq.shaded.io.grpc.netty.shaded.io.netty.util.internal.logging.Slf4JLoggerFactory;
import org.junit.jupiter.api.extension.RegisterExtension;

public class Netty40ServerTest extends AbstractHttpServerTest<ServerBootstrap> {

  @RegisterExtension
  static final InstrumentationExtension testing = HttpServerInstrumentationExtension.forAgent();

  static final LoggingHandler LOGGING_HANDLER;

  static {
    InternalLoggerFactory.setDefaultFactory(new Slf4JLoggerFactory());
    LOGGING_HANDLER = new LoggingHandler(Netty40ServerTest.class.getName());
  }

  @Override
  protected ServerBootstrap setupServer() {
    EventLoopGroup eventLoopGroup = new NioEventLoopGroup();

    return new ServerBootstrap()
        .group(eventLoopGroup)
        .handler(LOGGING_HANDLER)
        .childHandler(
            new ChannelInitializer<NioServerSocketChannel>() {
              @Override
              protected void initChannel(NioServerSocketChannel ch) throws Exception {
                ChannelPipeline pipeline = ch.pipeline();
                pipeline.addFirst("logger", LOGGING_HANDLER);

                pipeline.addLast(new HttpRequestDecoder(), new HttpResponseEncoder());
                pipeline.addLast(
                    new SimpleChannelInboundHandler<HttpRequest>() {

                      @Override
                      protected void channelRead0(ChannelHandlerContext ctx, HttpRequest msg) {
                        URI uri = URI.create(msg.getUri());
                        ServerEndpoint endpoint = ServerEndpoint.forPath(uri.getPath());
                        ctx.write(
                            controller(
                                endpoint,
                                () -> {
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
                                              HttpResponseStatus.valueOf(endpoint.getStatus()),
                                              content);
                                      break;
                                    case INDEXED_CHILD:
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
                                      break;
                                    case QUERY_PARAM:
                                      content =
                                          Unpooled.copiedBuffer(uri.getQuery(), CharsetUtil.UTF_8);
                                      response =
                                          new DefaultFullHttpResponse(
                                              HTTP_1_1,
                                              HttpResponseStatus.valueOf(endpoint.getStatus()),
                                              content);
                                      break;
                                    case REDIRECT:
                                      content = Unpooled.EMPTY_BUFFER;
                                      response =
                                          new DefaultFullHttpResponse(
                                              HTTP_1_1,
                                              HttpResponseStatus.valueOf(endpoint.getStatus()),
                                              content);
                                      response
                                          .headers()
                                          .set(HttpHeaders.Names.LOCATION, endpoint.getBody());
                                      break;
                                    case CAPTURE_HEADERS:
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
                                              msg.headers().get("X-Test-Request"));
                                      break;
                                    case EXCEPTION:
                                      throw new Exception(endpoint.getBody());
                                    default:
                                      content =
                                          Unpooled.copiedBuffer(
                                              NOT_FOUND.getBody(), CharsetUtil.UTF_8);
                                      response =
                                          new DefaultFullHttpResponse(
                                              HTTP_1_1,
                                              HttpResponseStatus.valueOf(NOT_FOUND.getStatus()),
                                              content);
                                      break;
                                  }
                                  response.headers().set(CONTENT_TYPE, "text/plain");
                                  if (content != null) {
                                    response.headers().set(CONTENT_LENGTH, content.readableBytes());
                                  }
                                  return response;
                                }));
                      }

                      @Override
                      public void exceptionCaught(ChannelHandlerContext ctx, Throwable ex) {
                        ByteBuf content = Unpooled.copiedBuffer(ex.getMessage(), CharsetUtil.UTF_8);
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
                    });
              }
            });
  }

  @Override
  protected void stopServer(ServerBootstrap server) {
    server.shutdown();
  }

  @Override
  protected void configure(HttpServerTestOptions options) {
    options.setHttpAttributes(
        serverEndpoint -> {
          Set<AttributeKey<?>> attributes =
              new HashSet<>(HttpServerTestOptions.DEFAULT_HTTP_ATTRIBUTES);
          attributes.remove(SemanticAttributes.HTTP_ROUTE);
          return attributes;
        });

    options.setExpectedException(new IllegalArgumentException(EXCEPTION.getBody()));
    options.setHasResponseCustomizer(serverEndpoint -> true);
  }
}
