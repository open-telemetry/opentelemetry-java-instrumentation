/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.common;

import com.google.auto.value.AutoValue;
import java.net.SocketAddress;
import javax.annotation.Nullable;

@AutoValue
public abstract class NettyConnectionRequest {

  public static NettyConnectionRequest resolve(SocketAddress remoteAddress) {
    return new AutoValue_NettyConnectionRequest("RESOLVE", Timer.start(), remoteAddress);
  }

  public static NettyConnectionRequest connect(SocketAddress remoteAddress) {
    return new AutoValue_NettyConnectionRequest("CONNECT", Timer.start(), remoteAddress);
  }

  public abstract String spanName();

  public abstract Timer timer();

  @Nullable
  public abstract SocketAddress remoteAddressOnStart();
}
