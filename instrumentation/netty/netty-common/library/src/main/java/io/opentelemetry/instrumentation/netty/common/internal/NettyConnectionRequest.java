/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.netty.common.internal;

import com.google.auto.value.AutoValue;
import java.net.SocketAddress;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
@AutoValue
public abstract class NettyConnectionRequest {

  public static NettyConnectionRequest resolve(SocketAddress remoteAddress) {
    return new AutoValue_NettyConnectionRequest("RESOLVE", remoteAddress);
  }

  public static NettyConnectionRequest connect(SocketAddress remoteAddress) {
    return new AutoValue_NettyConnectionRequest("CONNECT", remoteAddress);
  }

  public abstract String spanName();

  @Nullable
  public abstract SocketAddress remoteAddressOnStart();
}
