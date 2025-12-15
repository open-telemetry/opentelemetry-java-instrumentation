/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.net;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    DeclarativeConfigProperties generalConfig = DeclarativeConfigProperties.empty();
    if (openTelemetry instanceof ExtendedOpenTelemetry) {
      ExtendedOpenTelemetry extendedOpenTelemetry = (ExtendedOpenTelemetry) openTelemetry;
      DeclarativeConfigProperties instrumentationConfig =
          extendedOpenTelemetry.getConfigProvider().getInstrumentationConfig();
      if (instrumentationConfig != null) {
        generalConfig =
            instrumentationConfig.getStructured("general", DeclarativeConfigProperties.empty());
      }
    }

    List<DeclarativeConfigProperties> mappingList =
        generalConfig
            .getStructured("peer", DeclarativeConfigProperties.empty())
            .getStructuredList("service_mapping");

    if (mappingList != null) {
      for (DeclarativeConfigProperties mapping : mappingList) {
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
