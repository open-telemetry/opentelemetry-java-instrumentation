/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.net;

import static java.util.Comparator.comparing;
import static java.util.Comparator.naturalOrder;
import static java.util.Comparator.nullsFirst;

import com.google.auto.value.AutoValue;
import io.opentelemetry.instrumentation.api.incubator.semconv.net.internal.GlobUrlParser;
import io.opentelemetry.instrumentation.api.incubator.semconv.net.internal.UrlParser;
import io.opentelemetry.instrumentation.api.incubator.semconv.util.internal.GlobUtil;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.annotation.Nullable;

class PeerServiceResolverImpl implements PeerServiceResolver {

  private static final Comparator<ServiceMatcher> matcherComparator =
      nullsFirst(
              comparing(ServiceMatcher::getPort, nullsFirst(naturalOrder()))
                  .thenComparing(ServiceMatcher::getPath, nullsFirst(naturalOrder())))
          .reversed();

  private final Map<String, Map<ServiceMatcher, String>> mapping = new HashMap<>();
  private final Map<Predicate<String>, Map<ServiceMatcher, String>> globMapping =
      new LinkedHashMap<>();

  PeerServiceResolverImpl(Map<String, String> peerServiceMapping) {
    Map<String, Predicate<String>> globHostPredicates = new HashMap<>();

    peerServiceMapping.entrySet().stream()
        .sorted((o1, o2) -> Comparator.<String>naturalOrder().compare(o2.getKey(), o1.getKey()))
        .forEach(
            (entry) -> {
              String url = "https://" + entry.getKey();
              String serviceName = entry.getValue();

              String host = GlobUrlParser.getHost(url);
              if (host == null) {
                return;
              }

              if (host.contains("*") || host.contains("?")) {
                ServiceMatcher serviceMatcher =
                    ServiceMatcher.create(GlobUrlParser.getPort(url), GlobUrlParser.getPath(url));
                globMapping
                    .computeIfAbsent(
                        globHostPredicates.computeIfAbsent(host, GlobUtil::toGlobPatternPredicate),
                        x -> new TreeMap<>(matcherComparator))
                    .putIfAbsent(serviceMatcher, serviceName);
              } else {
                ServiceMatcher serviceMatcher =
                    ServiceMatcher.create(UrlParser.getPort(url), UrlParser.getPath(url));
                mapping
                    .computeIfAbsent(host, x -> new TreeMap<>(matcherComparator))
                    .putIfAbsent(serviceMatcher, serviceName);
              }
            });
  }

  @Override
  public boolean isEmpty() {
    return mapping.isEmpty() && globMapping.isEmpty();
  }

  @Nullable
  static String matchService(
      @Nullable Map<ServiceMatcher, String> matchers,
      @Nullable Integer port,
      @Nullable Supplier<String> pathSupplier) {
    if (matchers == null) {
      return null;
    }

    return matchers.entrySet().stream()
        .filter(entry -> entry.getKey().matches(port, pathSupplier))
        .findFirst()
        .map(Map.Entry::getValue)
        .orElse(null);
  }

  @Override
  @Nullable
  public String resolveService(
      String host, @Nullable Integer port, @Nullable Supplier<String> pathSupplier) {

    String service = matchService(mapping.get(host), port, pathSupplier);
    if (service != null) {
      return service;
    }

    for (Map.Entry<Predicate<String>, Map<ServiceMatcher, String>> entry : globMapping.entrySet()) {
      if (!entry.getKey().test(host)) {
        continue;
      }

      service = matchService(entry.getValue(), port, pathSupplier);
      if (service != null) {
        return service;
      }
    }

    return null;
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
