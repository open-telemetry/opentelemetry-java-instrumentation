/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.netty.common.v4_0.internal;

import com.google.auto.value.AutoValue;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.HttpRequest;
import java.net.SocketAddress;
import javax.annotation.Nullable;

/**
 * A tuple of an {@link HttpRequest} and a {@link Channel}.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
@AutoValue
public abstract class NettyRequest {

  /** Create a new {@link NettyRequest}. */
  public static NettyRequest create(HttpRequest request, Channel channel) {
    return new AutoValue_NettyRequest(request, channel, channel.remoteAddress());
  }

  /** Returns the {@link HttpRequest}. */
  public abstract HttpRequest getRequest();

  /**
   * @deprecated use {@link #getRequest()} instead.
   */
  @Deprecated
  public HttpRequest request() {
    return getRequest();
  }

  /** Returns the {@link Channel}. */
  public abstract Channel getChannel();

  /**
   * @deprecated use {@link #getChannel()} instead.
   */
  @Deprecated
  public Channel channel() {
    return getChannel();
  }

  /**
   * Return the {@link Channel#remoteAddress()} present when this {@link NettyRequest} was created.
   *
   * <p>We capture the remote address early because netty may return null when calling {@link
   * Channel#remoteAddress()} at the end of processing in cases of timeouts or other connection
   * issues.
   */
  @Nullable
  public abstract SocketAddress getRemoteAddress();

  /**
   * @deprecated use {@link #getRemoteAddress()} instead.
   */
  @Deprecated
  @Nullable
  public SocketAddress remoteAddress() {
    return getRemoteAddress();
  }
}
