/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.netty.v4_1;

import com.google.auto.value.AutoValue;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.HttpRequest;
import java.net.SocketAddress;
import javax.annotation.Nullable;

/** A wrapper that combines an {@link HttpRequest} with its associated {@link Channel}. */
@AutoValue
public abstract class NettyRequest {

  static NettyRequest create(
      io.opentelemetry.instrumentation.netty.common.v4_0.NettyRequest delegate) {
    return new AutoValue_NettyRequest(delegate);
  }

  abstract io.opentelemetry.instrumentation.netty.common.v4_0.NettyRequest delegate();

  /** Returns the HTTP request. */
  public HttpRequest getRequest() {
    return delegate().getRequest();
  }

  /** Returns the channel. */
  public Channel getChannel() {
    return delegate().getChannel();
  }

  /**
   * Returns the remote address captured when this request was created.
   *
   * <p>We capture the remote address early because netty may return null when calling {@link
   * Channel#remoteAddress()} at the end of processing in cases of timeouts or other connection
   * issues.
   *
   * @return the remote address, or null if not available
   */
  @Nullable
  public SocketAddress getRemoteAddress() {
    return delegate().getRemoteAddress();
  }
}
