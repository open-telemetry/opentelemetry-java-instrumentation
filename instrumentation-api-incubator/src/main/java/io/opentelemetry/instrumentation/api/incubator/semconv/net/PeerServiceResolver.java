/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.net;

import static java.util.Collections.emptyList;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.api.incubator.semconv.service.peer.internal.ServicePeerResolver;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import javax.annotation.Nullable;

/**
 * @deprecated Use {@link
 *     io.opentelemetry.instrumentation.api.incubator.semconv.service.peer.ServicePeerAttributesExtractor}
 *     instead.
 */
@Deprecated
public interface PeerServiceResolver {

  boolean isEmpty();

  @Nullable
  String resolveService(String host, @Nullable Integer port, Supplier<String> pathSupplier);

  static PeerServiceResolver create(Map<String, String> mapping) {
    ServicePeerResolver delegate = new ServicePeerResolver(mapping);
    return new PeerServiceResolver() {
      @Override
      public boolean isEmpty() {
        return delegate.isEmpty();
      }

      @Override
      @Nullable
      public String resolveService(
          String host, @Nullable Integer port, Supplier<String> pathSupplier) {
        return delegate.resolveServiceName(host, port, pathSupplier);
      }
    };
  }

  static PeerServiceResolver create(OpenTelemetry openTelemetry) {
    Map<String, String> peerServiceMap = new HashMap<>();

    DeclarativeConfigUtil.getGeneralInstrumentationConfig(openTelemetry)
        .get("peer")
        .getStructuredList("service_mapping", emptyList())
        .forEach(
            mapping -> {
              String peer = mapping.getString("peer");
              String service = mapping.getString("service");
              if (peer != null && service != null) {
                peerServiceMap.put(peer, service);
              }
            });

    return create(peerServiceMap);
  }
}
