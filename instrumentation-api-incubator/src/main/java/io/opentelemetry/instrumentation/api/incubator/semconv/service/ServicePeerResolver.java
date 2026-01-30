/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.service;

import static java.util.Collections.emptyList;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.api.incubator.semconv.service.internal.ServicePeerResolverImpl;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import javax.annotation.Nullable;

public interface ServicePeerResolver {

  boolean isEmpty();

  @Nullable
  String resolveService(String host, @Nullable Integer port, Supplier<String> pathSupplier);

  static ServicePeerResolver create(Map<String, String> mapping) {
    return new ServicePeerResolverImpl(mapping);
  }

  static ServicePeerResolver create(OpenTelemetry openTelemetry) {
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

    return new ServicePeerResolverImpl(peerServiceMap);
  }
}
