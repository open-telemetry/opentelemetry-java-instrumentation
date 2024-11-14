/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v3_8.server;

import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.CAPTURE_HEADERS;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.ERROR;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.EXCEPTION;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.INDEXED_CHILD;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.NOT_FOUND;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.QUERY_PARAM;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.REDIRECT;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.SUCCESS;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.forPath;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_ROUTE;
import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.CONTENT_LENGTH;
import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;
import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.LOCATION;
import static org.jboss.netty.handler.codec.http.HttpVersion.HTTP_1_1;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions;
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.HashSet;
import java.util.Set;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.DefaultChannelPipeline;
import org.jboss.netty.channel.DownstreamMessageEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.FailedChannelFuture;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.channel.SucceededChannelFuture;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpServerCodec;
import org.jboss.netty.handler.codec.http.QueryStringDecoder;
import org.jboss.netty.handler.logging.LoggingHandler;
import org.jboss.netty.logging.InternalLogLevel;
import org.jboss.netty.logging.InternalLoggerFactory;
import org.jboss.netty.logging.Slf4JLoggerFactory;
import org.jboss.netty.util.CharsetUtil;
import org.junit.jupiter.api.extension.RegisterExtension;

class Netty38ServerTest extends AbstractHttpServerTest<ServerBootstrap> {

  @RegisterExtension
  static final InstrumentationExtension testing = HttpServerInstrumentationExtension.forAgent();

  static final LoggingHandler LOGGING_HANDLER;

  static {
    InternalLoggerFactory.setDefaultFactory(new Slf4JLoggerFactory());
    LOGGING_HANDLER =
        new LoggingHandler(Netty38ServerTest.class.getName(), InternalLogLevel.DEBUG, true);
  }

  @Override
  protected ServerBootstrap setupServer() {
    ServerBootstrap bootstrap = new ServerBootstrap(new NioServerSocketChannelFactory());
    bootstrap.setParentHandler(LOGGING_HANDLER);
    bootstrap.setPipelineFactory(Netty38ServerTest::channelPipeline);

    InetSocketAddress address = new InetSocketAddress(port);
    bootstrap.bind(address);
    return bootstrap;
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
          attributes.remove(HTTP_ROUTE);
          return attributes;
        });

    options.setExpectedException(new IllegalArgumentException(EXCEPTION.getBody()));
    options.setHasResponseCustomizer(serverEndpoint -> true);
  }

  private static ChannelPipeline channelPipeline() {
    ChannelPipeline channelPipeline = new DefaultChannelPipeline();
    channelPipeline.addFirst("logger", LOGGING_HANDLER);

    channelPipeline.addLast("http-codec", new HttpServerCodec());
    channelPipeline.addLast(
        "controller",
        new SimpleChannelHandler() {
          @Override
          public void messageReceived(ChannelHandlerContext ctx, MessageEvent msg) {
            if (msg.getMessage() instanceof HttpRequest) {
              HttpRequest request = (HttpRequest) msg.getMessage();
              URI uri = URI.create(request.getUri());
              ServerEndpoint endpoint = forPath(uri.getPath());
              ctx.sendDownstream(
                  controller(
                      endpoint,
                      () -> {
                        HttpResponse response;
                        ChannelBuffer responseContent;
                        if (SUCCESS.equals(endpoint) || ERROR.equals(endpoint)) {
                          responseContent =
                              ChannelBuffers.copiedBuffer(endpoint.getBody(), CharsetUtil.UTF_8);
                          response =
                              new DefaultHttpResponse(
                                  HTTP_1_1, HttpResponseStatus.valueOf(endpoint.getStatus()));
                          response.setContent(responseContent);
                        } else if (INDEXED_CHILD.equals(endpoint)) {
                          responseContent = ChannelBuffers.EMPTY_BUFFER;
                          endpoint.collectSpanAttributes(
                              name ->
                                  new QueryStringDecoder(uri)
                                      .getParameters().get(name).stream().findFirst().orElse(null));
                          response =
                              new DefaultHttpResponse(
                                  HTTP_1_1, HttpResponseStatus.valueOf(endpoint.getStatus()));
                          response.setContent(responseContent);
                        } else if (QUERY_PARAM.equals(endpoint)) {
                          responseContent =
                              ChannelBuffers.copiedBuffer(uri.getQuery(), CharsetUtil.UTF_8);
                          response =
                              new DefaultHttpResponse(
                                  HTTP_1_1, HttpResponseStatus.valueOf(endpoint.getStatus()));
                          response.setContent(responseContent);
                        } else if (REDIRECT.equals(endpoint)) {
                          responseContent = ChannelBuffers.EMPTY_BUFFER;
                          response =
                              new DefaultHttpResponse(
                                  HTTP_1_1, HttpResponseStatus.valueOf(endpoint.getStatus()));
                          response.setContent(responseContent);
                          response.headers().set(LOCATION, endpoint.getBody());
                        } else if (CAPTURE_HEADERS.equals(endpoint)) {
                          responseContent =
                              ChannelBuffers.copiedBuffer(endpoint.getBody(), CharsetUtil.UTF_8);
                          response =
                              new DefaultHttpResponse(
                                  HTTP_1_1, HttpResponseStatus.valueOf(endpoint.getStatus()));
                          response
                              .headers()
                              .set("X-Test-Response", request.headers().get("X-Test-Request"));
                          response.setContent(responseContent);
                        } else if (EXCEPTION.equals(endpoint)) {
                          throw new IllegalArgumentException(endpoint.getBody());
                        } else {
                          responseContent =
                              ChannelBuffers.copiedBuffer(NOT_FOUND.getBody(), CharsetUtil.UTF_8);
                          response =
                              new DefaultHttpResponse(
                                  HTTP_1_1, HttpResponseStatus.valueOf(endpoint.getStatus()));
                          response.setContent(responseContent);
                        }
                        response.headers().set(CONTENT_TYPE, "text/plain");
                        if (responseContent != null) {
                          response.headers().set(CONTENT_LENGTH, responseContent.readableBytes());
                        }
                        return new DownstreamMessageEvent(
                            ctx.getChannel(),
                            new SucceededChannelFuture(ctx.getChannel()),
                            response,
                            ctx.getChannel().getRemoteAddress());
                      }));
            }
          }

          @Override
          public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent ex) {
            String message =
                ex.getCause() == null
                    ? "<no cause>"
                    : ex.getCause().getMessage() == null ? "<null>" : ex.getCause().getMessage();
            ChannelBuffer buffer = ChannelBuffers.copiedBuffer(message, CharsetUtil.UTF_8);
            HttpResponse response =
                new DefaultHttpResponse(HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR);
            response.setContent(buffer);
            response.headers().set(CONTENT_TYPE, "text/plain");
            response.headers().set(CONTENT_LENGTH, buffer.readableBytes());
            ctx.sendDownstream(
                new DownstreamMessageEvent(
                    ctx.getChannel(),
                    new FailedChannelFuture(ctx.getChannel(), ex.getCause()),
                    response,
                    ctx.getChannel().getRemoteAddress()));
          }
        });

    return channelPipeline;
  }
}
