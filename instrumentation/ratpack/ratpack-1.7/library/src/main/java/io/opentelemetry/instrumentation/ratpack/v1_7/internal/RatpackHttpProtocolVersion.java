/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ratpack.v1_7.internal;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpResponse;
import io.opentelemetry.instrumentation.api.internal.HttpProtocolUtil;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import javax.annotation.Nullable;
import ratpack.http.client.RequestSpec;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class RatpackHttpProtocolVersion extends ChannelInboundHandlerAdapter {

  private static final String HANDLER_NAME = RatpackHttpProtocolVersion.class.getName();
  private static final String REDIRECT_HANDLER_NAME = "redirect";
  private static final VirtualField<RequestSpec, RatpackHttpProtocolVersion>
      REQUEST_PROTOCOL_VERSION =
          VirtualField.find(RequestSpec.class, RatpackHttpProtocolVersion.class);

  private final Channel channel;
  @Nullable private volatile String protocolVersion;

  public static void attach(RequestSpec request, Channel channel) {
    RatpackHttpProtocolVersion previous = REQUEST_PROTOCOL_VERSION.get(request);
    if (previous != null) {
      previous.removeFromPipeline();
    }

    ChannelPipeline pipeline = channel.pipeline();
    ChannelHandler existingHandler = pipeline.get(HANDLER_NAME);
    if (existingHandler instanceof RatpackHttpProtocolVersion) {
      ((RatpackHttpProtocolVersion) existingHandler).removeFromPipeline();
    }

    RatpackHttpProtocolVersion handler = new RatpackHttpProtocolVersion(channel);
    REQUEST_PROTOCOL_VERSION.set(request, handler);
    pipeline.addBefore(REDIRECT_HANDLER_NAME, HANDLER_NAME, handler);
  }

  @Nullable
  public static String get(RequestSpec request) {
    RatpackHttpProtocolVersion handler = REQUEST_PROTOCOL_VERSION.get(request);
    return handler != null ? handler.protocolVersion : null;
  }

  public static void clearRequest(RequestSpec request) {
    RatpackHttpProtocolVersion handler = REQUEST_PROTOCOL_VERSION.get(request);
    REQUEST_PROTOCOL_VERSION.set(request, null);
    if (handler != null) {
      handler.removeFromPipeline();
    }
  }

  private RatpackHttpProtocolVersion(Channel channel) {
    this.channel = channel;
  }

  @Override
  public void channelRead(ChannelHandlerContext context, Object message) {
    if (message instanceof HttpResponse) {
      HttpResponse response = (HttpResponse) message;
      int statusCode = response.status().code();
      if (statusCode < 100 || statusCode >= 200) {
        protocolVersion = HttpProtocolUtil.getVersion(response.protocolVersion().text());
        removeFromPipeline();
      }
    }
    context.fireChannelRead(message);
  }

  @Override
  public void channelInactive(ChannelHandlerContext context) {
    removeFromPipeline();
    context.fireChannelInactive();
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext context, Throwable cause) {
    removeFromPipeline();
    context.fireExceptionCaught(cause);
  }

  private void removeFromPipeline() {
    ChannelPipeline pipeline = channel.pipeline();
    if (pipeline.context(this) != null) {
      pipeline.remove(this);
    }
  }
}
