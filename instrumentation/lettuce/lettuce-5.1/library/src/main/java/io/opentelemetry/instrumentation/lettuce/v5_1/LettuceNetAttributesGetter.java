/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.lettuce.v5_1;

import io.opentelemetry.instrumentation.api.instrumenter.net.InetSocketAddressNetClientAttributesGetter;
import io.opentelemetry.instrumentation.lettuce.v5_1.OpenTelemetryTracing.OpenTelemetryEndpoint;
import java.net.InetSocketAddress;
import javax.annotation.Nullable;

final class LettuceNetAttributesGetter
    extends InetSocketAddressNetClientAttributesGetter<OpenTelemetryEndpoint, Void> {

  @Nullable
  @Override
  public String getPeerName(OpenTelemetryEndpoint openTelemetryEndpoint) {
    return null;
  }

  @Nullable
  @Override
  public Integer getPeerPort(OpenTelemetryEndpoint openTelemetryEndpoint) {
    return null;
  }

  @Nullable
  @Override
  protected InetSocketAddress getPeerSocketAddress(
      OpenTelemetryEndpoint openTelemetryEndpoint, @Nullable Void unused) {
    return openTelemetryEndpoint.address;
  }
}
