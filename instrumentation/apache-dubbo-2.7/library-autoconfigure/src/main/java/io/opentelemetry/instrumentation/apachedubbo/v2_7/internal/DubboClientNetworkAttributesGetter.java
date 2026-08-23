/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v2_7.internal;

import static io.opentelemetry.instrumentation.apachedubbo.v2_7.internal.DubboRegistryUtil.buildServiceTarget;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableRpcSemconv;

import io.opentelemetry.instrumentation.apachedubbo.v2_7.DubboRequest;
import io.opentelemetry.instrumentation.api.semconv.network.NetworkAttributesGetter;
import io.opentelemetry.instrumentation.api.semconv.network.ServerAttributesGetter;
import java.net.InetSocketAddress;
import javax.annotation.Nullable;
import org.apache.dubbo.common.URL;
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
    // the registry address is the logical target only under the stable rpc semconv; keep the
    // resolved provider host under the old semconv to avoid changing already-emitted attributes
    String registryAddress = request.registryAddress();
    if (registryAddress != null && emitStableRpcSemconv()) {
      return registryAddress + "/" + buildServiceTarget(request.url());
    }
    URL url = request.url();
    return url != null ? url.getHost() : null;
  }

  @Nullable
  @Override
  public Integer getServerPort(DubboRequest request) {
    if (request.registryAddress() != null && emitStableRpcSemconv()) {
      return null;
    }
    URL url = request.url();
    if (url == null) {
      return null;
    }
    int port = url.getPort();
    return port > 0 ? port : null;
  }

  @Override
  @Nullable
  public InetSocketAddress getNetworkPeerInetSocketAddress(
      DubboRequest request, @Nullable Result response) {
    return request.remoteAddress();
  }
}
