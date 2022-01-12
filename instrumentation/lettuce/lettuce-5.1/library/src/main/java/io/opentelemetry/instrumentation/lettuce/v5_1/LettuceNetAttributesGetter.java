/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.lettuce.v5_1;

import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.NetTransportValues.IP_TCP;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter;
import io.opentelemetry.instrumentation.lettuce.v5_1.OpenTelemetryTracing.OpenTelemetryEndpoint;
import javax.annotation.Nullable;

final class LettuceNetAttributesGetter
    implements NetClientAttributesGetter<OpenTelemetryEndpoint, Void> {

  @Override
  public String transport(OpenTelemetryEndpoint endpoint, @Nullable Void unused) {
    return IP_TCP;
  }

  @Nullable
  @Override
  public String peerName(OpenTelemetryEndpoint endpoint, @Nullable Void unused) {
    return endpoint.name;
  }

  @Override
  public Integer peerPort(OpenTelemetryEndpoint endpoint, @Nullable Void unused) {
    return endpoint.port;
  }

  @Nullable
  @Override
  public String peerIp(OpenTelemetryEndpoint endpoint, @Nullable Void unused) {
    return endpoint.ip;
  }
}
