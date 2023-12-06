/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.lettuce.v5_1;

import io.opentelemetry.instrumentation.api.semconv.network.NetworkAttributeGetter;
import io.opentelemetry.instrumentation.lettuce.v5_1.OpenTelemetryTracing.OpenTelemetryEndpoint;
import java.net.InetSocketAddress;
import javax.annotation.Nullable;

final class LettuceNetworkAttributeGetter
    implements NetworkAttributeGetter<OpenTelemetryEndpoint, Void> {

  @Nullable
  @Override
  public InetSocketAddress getNetworkPeerInetSocketAddress(
      OpenTelemetryEndpoint openTelemetryEndpoint, @Nullable Void unused) {
    return openTelemetryEndpoint.address;
  }
}
