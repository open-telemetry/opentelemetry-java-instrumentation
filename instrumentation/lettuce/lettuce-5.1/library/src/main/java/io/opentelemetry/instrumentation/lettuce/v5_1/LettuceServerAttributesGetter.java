/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.lettuce.v5_1;

import io.opentelemetry.instrumentation.api.semconv.network.ServerAttributesGetter;
import java.net.InetSocketAddress;
import javax.annotation.Nullable;

final class LettuceServerAttributesGetter implements ServerAttributesGetter<LettuceRequest> {

  @Nullable
  @Override
  public String getServerAddress(LettuceRequest request) {
    InetSocketAddress address = request.getAddress();
    if (address != null) {
      return address.getHostString();
    }
    return null;
  }

  @Nullable
  @Override
  public Integer getServerPort(LettuceRequest request) {
    InetSocketAddress address = request.getAddress();
    if (address != null) {
      return address.getPort();
    }
    return null;
  }
}
