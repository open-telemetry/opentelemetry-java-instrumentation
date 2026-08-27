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
import io.netty.handler.codec.http.HttpVersion;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import java.lang.ref.WeakReference;
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

  private final WeakReference<RequestSpec> request;
  private final Channel channel;
  @Nullable private volatile String protocolVersion;

  public static void attach(RequestSpec request, Channel channel) {
    RatpackHttpProtocolVersion previous = REQUEST_PROTOCOL_VERSION.get(request);
    if (previous != null) {
      previous.clear();
    }

    ChannelPipeline pipeline = channel.pipeline();
    ChannelHandler existingHandler = pipeline.get(HANDLER_NAME);
    if (existingHandler instanceof RatpackHttpProtocolVersion) {
      ((RatpackHttpProtocolVersion) existingHandler).clear();
    }

    RatpackHttpProtocolVersion handler = new RatpackHttpProtocolVersion(request, channel);
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
    if (handler != null) {
      handler.clear();
    }
  }

  private RatpackHttpProtocolVersion(RequestSpec request, Channel channel) {
    this.request = new WeakReference<>(request);
    this.channel = channel;
  }

  @Override
  public void channelRead(ChannelHandlerContext context, Object message) {
    if (message instanceof HttpResponse) {
      HttpResponse response = (HttpResponse) message;
      int statusCode = response.status().code();
      if (statusCode < 100 || statusCode >= 200) {
        protocolVersion = normalize(response.protocolVersion());
        removeFromPipeline();
      }
    }
    context.fireChannelRead(message);
  }

  @Override
  public void channelInactive(ChannelHandlerContext context) {
    clear();
    context.fireChannelInactive();
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext context, Throwable cause) {
    clear();
    context.fireExceptionCaught(cause);
  }

  private void clear() {
    RequestSpec request = this.request.get();
    if (request != null && REQUEST_PROTOCOL_VERSION.get(request) == this) {
      REQUEST_PROTOCOL_VERSION.set(request, null);
    }
    removeFromPipeline();
  }

  private void removeFromPipeline() {
    ChannelPipeline pipeline = channel.pipeline();
    if (pipeline.context(this) != null) {
      pipeline.remove(this);
    }
  }

  private static String normalize(HttpVersion version) {
    int major = version.majorVersion();
    int minor = version.minorVersion();
    return minor == 0 ? Integer.toString(major) : major + "." + minor;
  }
}
