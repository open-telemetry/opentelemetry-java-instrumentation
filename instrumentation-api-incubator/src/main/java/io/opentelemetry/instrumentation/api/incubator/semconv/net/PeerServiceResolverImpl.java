/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.net;

import io.opentelemetry.instrumentation.api.incubator.semconv.service.ServicePeerResolver;
import io.opentelemetry.instrumentation.api.incubator.semconv.service.internal.ServicePeerResolverImpl;
import java.util.Map;
import java.util.function.Supplier;
import javax.annotation.Nullable;

@SuppressWarnings("deprecation") // using deprecated PeerServiceResolver interface
class PeerServiceResolverImpl implements PeerServiceResolver {

  private final ServicePeerResolver delegate;

  PeerServiceResolverImpl(Map<String, String> peerServiceMapping) {
    this.delegate = new ServicePeerResolverImpl(peerServiceMapping);
  }

  PeerServiceResolverImpl(ServicePeerResolver delegate) {
    this.delegate = delegate;
  }

  @Override
  public boolean isEmpty() {
    return delegate.isEmpty();
  }

  @Override
  @Nullable
  public String resolveService(String host, @Nullable Integer port, Supplier<String> pathSupplier) {
    return delegate.resolveService(host, port, pathSupplier);
  }
}
