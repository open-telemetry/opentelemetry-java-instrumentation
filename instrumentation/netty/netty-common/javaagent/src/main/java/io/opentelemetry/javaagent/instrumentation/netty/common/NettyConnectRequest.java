/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.common;

import com.google.auto.value.AutoValue;
import java.net.SocketAddress;
import javax.annotation.Nullable;

@AutoValue
public abstract class NettyConnectRequest {

  public static NettyConnectRequest create(SocketAddress remoteAddress) {
    return new AutoValue_NettyConnectRequest(Timer.start(), remoteAddress);
  }

  public abstract Timer timer();

  @Nullable
  public abstract SocketAddress remoteAddressOnStart();
}
