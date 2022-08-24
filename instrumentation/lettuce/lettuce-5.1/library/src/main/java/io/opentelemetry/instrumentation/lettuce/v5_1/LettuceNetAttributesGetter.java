/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.lettuce.v5_1;

import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.NetTransportValues.IP_TCP;

import io.opentelemetry.instrumentation.api.instrumenter.net.InetSocketAddressNetClientAttributesGetter;
import io.opentelemetry.instrumentation.lettuce.v5_1.OpenTelemetryTracing.OpenTelemetryEndpoint;
import java.net.InetSocketAddress;
import javax.annotation.Nullable;

final class LettuceNetAttributesGetter
    extends InetSocketAddressNetClientAttributesGetter<OpenTelemetryEndpoint, Void> {

  @Override
  public String transport(OpenTelemetryEndpoint endpoint, @Nullable Void unused) {
    return IP_TCP;
  }

  @Nullable
  @Override
  public String peerName(OpenTelemetryEndpoint openTelemetryEndpoint, @Nullable Void unused) {
    return null;
  }

  @Nullable
  @Override
  public Integer peerPort(OpenTelemetryEndpoint openTelemetryEndpoint, @Nullable Void unused) {
    return null;
  }

  @Nullable
  @Override
  public InetSocketAddress getPeerAddress(
      OpenTelemetryEndpoint openTelemetryEndpoint, @Nullable Void unused) {
    return openTelemetryEndpoint.address;
  }
}
