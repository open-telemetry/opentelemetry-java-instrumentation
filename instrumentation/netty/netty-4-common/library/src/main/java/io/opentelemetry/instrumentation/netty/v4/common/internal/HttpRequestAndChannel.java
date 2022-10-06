/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.netty.v4.common.internal;

import com.google.auto.value.AutoValue;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.HttpRequest;
import java.net.SocketAddress;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
@AutoValue
public abstract class HttpRequestAndChannel {

  public static HttpRequestAndChannel create(HttpRequest request, Channel channel) {
    return new AutoValue_HttpRequestAndChannel(request, channel, channel.remoteAddress());
  }

  public abstract HttpRequest request();

  public abstract Channel channel();

  // we're capturing the remote address early because in case of timeouts or other connection issues
  // netty may return null when calling Channel.remoteAddress() at the end of processing
  @Nullable
  public abstract SocketAddress remoteAddress();
}
