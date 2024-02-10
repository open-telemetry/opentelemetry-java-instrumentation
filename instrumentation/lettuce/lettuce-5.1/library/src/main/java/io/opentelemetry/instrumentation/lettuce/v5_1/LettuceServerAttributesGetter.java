/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.lettuce.v5_1;

import io.opentelemetry.instrumentation.api.semconv.network.NetworkAttributesGetter;
import io.opentelemetry.instrumentation.api.semconv.network.ServerAttributesGetter;
import io.opentelemetry.instrumentation.lettuce.v5_1.OpenTelemetryTracing.OpenTelemetryEndpoint;
import java.net.InetSocketAddress;
import javax.annotation.Nullable;

class LettuceServerAttributesGetter
    implements ServerAttributesGetter<OpenTelemetryEndpoint>,
        NetworkAttributesGetter<OpenTelemetryEndpoint, Void> {

  @Nullable
  @Override
  public String getServerAddress(OpenTelemetryEndpoint request) {
    if (request.address != null) {
      return request.address.getHostString();
    }
    return null;
  }

  @Nullable
  @Override
  public Integer getServerPort(OpenTelemetryEndpoint request) {
    if (request.address != null) {
      return request.address.getPort();
    }
    return null;
  }

  @Nullable
  @Override
  public InetSocketAddress getNetworkPeerInetSocketAddress(
      OpenTelemetryEndpoint openTelemetryEndpoint, @Nullable Void unused) {
    return openTelemetryEndpoint.address;
  }
}
