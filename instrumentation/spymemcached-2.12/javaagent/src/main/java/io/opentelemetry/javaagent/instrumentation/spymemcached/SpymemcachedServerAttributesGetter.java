/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spymemcached;

import io.opentelemetry.instrumentation.api.semconv.network.ServerAttributesGetter;
import java.net.InetSocketAddress;
import javax.annotation.Nullable;

final class SpymemcachedServerAttributesGetter
    implements ServerAttributesGetter<SpymemcachedRequest> {

  @Nullable
  @Override
  public String getServerAddress(SpymemcachedRequest request) {
    InetSocketAddress address = request.getHandlingNodeAddress();
    return address != null ? address.getHostString() : null;
  }

  @Nullable
  @Override
  public Integer getServerPort(SpymemcachedRequest request) {
    InetSocketAddress address = request.getHandlingNodeAddress();
    return address != null ? address.getPort() : null;
  }
}
