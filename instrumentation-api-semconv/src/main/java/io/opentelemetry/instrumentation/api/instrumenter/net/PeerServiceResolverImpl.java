/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.net;

import static java.util.Comparator.comparing;
import static java.util.Comparator.naturalOrder;
import static java.util.Comparator.nullsFirst;

import com.google.auto.value.AutoValue;
import io.opentelemetry.instrumentation.api.instrumenter.net.internal.UrlParser;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import javax.annotation.Nullable;

class PeerServiceResolverImpl implements PeerServiceResolver {

  private static final Comparator<ServiceMatcher> matcherComparator =
      nullsFirst(
          comparing(ServiceMatcher::getPort, nullsFirst(naturalOrder()))
              .thenComparing(comparing(ServiceMatcher::getPath, nullsFirst(naturalOrder()))));

  private final Map<String, Map<ServiceMatcher, String>> mapping = new HashMap<>();

  PeerServiceResolverImpl(Map<String, String> peerServiceMapping) {
    peerServiceMapping.forEach(
        (key, serviceName) -> {
          String url = "https://" + key;
          String host = UrlParser.getHost(url);
          Integer port = UrlParser.getPort(url);
          String path = UrlParser.getPath(url);
          Map<ServiceMatcher, String> matchers =
              mapping.computeIfAbsent(host, x -> new HashMap<>());
          matchers.putIfAbsent(ServiceMatcher.create(port, path), serviceName);
        });
  }

  @Override
  public boolean isEmpty() {
    return mapping.isEmpty();
  }

  @Override
  @Nullable
  public String resolveService(
      String host, @Nullable Integer port, @Nullable Supplier<String> pathSupplier) {
    Map<ServiceMatcher, String> matchers = mapping.get(host);
    if (matchers == null) {
      return null;
    }
    return matchers.entrySet().stream()
        .filter(entry -> entry.getKey().matches(port, pathSupplier))
        .max((o1, o2) -> matcherComparator.compare(o1.getKey(), o2.getKey()))
        .map(Map.Entry::getValue)
        .orElse(null);
  }

  @AutoValue
  abstract static class ServiceMatcher {

    static ServiceMatcher create(Integer port, String path) {
      return new AutoValue_PeerServiceResolverImpl_ServiceMatcher(port, path);
    }

    @Nullable
    abstract Integer getPort();

    @Nullable
    abstract String getPath();

    public boolean matches(Integer port, Supplier<String> pathSupplier) {
      if (this.getPort() != null) {
        if (!this.getPort().equals(port)) {
          return false;
        }
      }
      if (this.getPath() != null && this.getPath().length() > 0) {
        if (pathSupplier == null) {
          return false;
        }
        String path = pathSupplier.get();
        if (path == null) {
          return false;
        }
        if (!path.startsWith(this.getPath())) {
          return false;
        }
        if (port != null) {
          return port.equals(this.getPort());
        }
      }
      return true;
    }
  }
}
