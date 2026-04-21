/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v2_7.internal;

import static io.opentelemetry.instrumentation.apachedubbo.v2_7.internal.DubboRegistryUtil.buildServiceTarget;

import io.opentelemetry.instrumentation.apachedubbo.v2_7.DubboRequest;
import io.opentelemetry.instrumentation.api.semconv.network.NetworkAttributesGetter;
import io.opentelemetry.instrumentation.api.semconv.network.ServerAttributesGetter;
import java.net.InetSocketAddress;
import javax.annotation.Nullable;
import org.apache.dubbo.rpc.Result;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class DubboClientNetworkAttributesGetter
    implements ServerAttributesGetter<DubboRequest>, NetworkAttributesGetter<DubboRequest, Result> {

  @Nullable
  @Override
  public String getServerAddress(DubboRequest request) {
    String registryAddress = request.registryAddress();
    if (registryAddress != null) {
      return registryAddress + "/" + buildServiceTarget(request.url());
    }
    return request.url().getHost();
  }

  @Nullable
  @Override
  public Integer getServerPort(DubboRequest request) {
    if (request.registryAddress() != null) {
      return null;
    }
    return request.url().getPort();
  }

  @Override
  @Nullable
  public InetSocketAddress getNetworkPeerInetSocketAddress(
      DubboRequest request, @Nullable Result response) {
    return request.remoteAddress();
  }
}
