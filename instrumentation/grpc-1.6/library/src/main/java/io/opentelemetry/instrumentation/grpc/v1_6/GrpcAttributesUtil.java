/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_6;

import io.grpc.Attributes;
import io.grpc.Grpc;
import java.net.SocketAddress;
import javax.annotation.Nullable;

final class GrpcAttributesUtil {

  @Nullable
  private static final Attributes.Key<SocketAddress> TRANSPORT_ATTR_LOCAL_ADDR_KEY =
      findTransportAttrLocalAddrKey();

  @Nullable
  @SuppressWarnings("unchecked") // The gRPC field is declared as Attributes.Key<SocketAddress>.
  private static Attributes.Key<SocketAddress> findTransportAttrLocalAddrKey() {
    try {
      // The key was introduced in gRPC 1.16. Use the actual key, which has identity semantics.
      return (Attributes.Key<SocketAddress>)
          Grpc.class.getField("TRANSPORT_ATTR_LOCAL_ADDR").get(null);
    } catch (NoSuchFieldException | IllegalAccessException ignored) {
      return null;
    }
  }

  @Nullable
  static SocketAddress getLocalSocketAddress(Attributes attributes) {
    return TRANSPORT_ATTR_LOCAL_ADDR_KEY == null
        ? null
        : attributes.get(TRANSPORT_ATTR_LOCAL_ADDR_KEY);
  }

  private GrpcAttributesUtil() {}
}
