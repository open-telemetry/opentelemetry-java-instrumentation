/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.influxdb.v2_4;

import io.opentelemetry.instrumentation.api.semconv.network.NetworkAttributesGetter;
import java.net.InetSocketAddress;
import org.jetbrains.annotations.Nullable;

final class InfluxDbNetAttributesGetter implements NetworkAttributesGetter<InfluxDbRequest, Void> {

  @Nullable
  @Override
  public InetSocketAddress getNetworkPeerInetSocketAddress(
      InfluxDbRequest influxDbRequest, @Nullable Void unused) {
    return influxDbRequest.getAddress();
  }
}
