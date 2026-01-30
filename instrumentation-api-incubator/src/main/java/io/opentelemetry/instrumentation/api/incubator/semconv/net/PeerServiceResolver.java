/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.net;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.service.ServicePeerResolver;
import java.util.Map;

/**
 * @deprecated Use {@link ServicePeerResolver} instead.
 */
@Deprecated
public interface PeerServiceResolver extends ServicePeerResolver {

  static PeerServiceResolver create(Map<String, String> mapping) {
    return new PeerServiceResolverImpl(mapping);
  }

  static PeerServiceResolver create(OpenTelemetry openTelemetry) {
    return new PeerServiceResolverImpl(ServicePeerResolver.create(openTelemetry));
  }
}
