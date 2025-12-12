/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.net;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import javax.annotation.Nullable;

public interface PeerServiceResolver {

  public boolean isEmpty();

  @Nullable
  public String resolveService(String host, @Nullable Integer port, Supplier<String> pathSupplier);

  static PeerServiceResolver create(Map<String, String> mapping) {
    return new PeerServiceResolverImpl(mapping);
  }

  static PeerServiceResolver create(OpenTelemetry openTelemetry) {
    Map<String, String> peerServiceMap = new HashMap<>();

    Optional<List<DeclarativeConfigProperties>> mappingList =
        DeclarativeConfigUtil.getStructuredList(
            openTelemetry, "general", "peer", "service_mapping");

    if (mappingList.isPresent()) {
      for (DeclarativeConfigProperties mapping : mappingList.get()) {
        String peer = mapping.getString("peer");
        String service = mapping.getString("service");
        if (peer != null && service != null) {
          peerServiceMap.put(peer, service);
        }
      }
    }

    return new PeerServiceResolverImpl(peerServiceMap);
  }
}
