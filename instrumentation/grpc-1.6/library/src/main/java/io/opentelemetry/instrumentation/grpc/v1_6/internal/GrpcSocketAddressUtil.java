/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_6.internal;

import io.opentelemetry.instrumentation.api.semconv.network.internal.SocketAddressUtil;
import java.net.SocketAddress;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class GrpcSocketAddressUtil {

  @Nullable
  public static SocketAddress normalize(@Nullable SocketAddress address) {
    return SocketAddressUtil.normalizeNettyUnixSocketAddress(address);
  }

  private GrpcSocketAddressUtil() {}
}
