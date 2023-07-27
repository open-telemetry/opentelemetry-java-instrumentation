/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import static java.util.Comparator.comparing;
import static java.util.Comparator.naturalOrder;
import static java.util.Comparator.nullsFirst;

import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import javax.annotation.Nullable;

class PeerServiceResolverImpl implements PeerServiceResolver {

  private static final Logger logger = Logger.getLogger(PeerServiceResolverImpl.class.getName());

  private static final Map<String, Map<ServiceMatcher, String>> mapping = new ConcurrentHashMap<>();

  private static final Comparator<ServiceMatcher> matcherComparator =
      nullsFirst(
          comparing(ServiceMatcher::getPort, nullsFirst(naturalOrder()))
              .thenComparing(comparing(ServiceMatcher::getPath, nullsFirst(naturalOrder()))));

  PeerServiceResolverImpl(Map<String, String> peerServiceMapping) {
    peerServiceMapping.forEach(
        (key, serviceName) -> {
          try {
            URI uri;
            if (key.contains("://")) {
              uri = new URI(key);
            } else {
              uri = new URI("http://" + key);
            }
            String host = uri.getHost();
            Integer port = uri.getPort() == -1 ? null : uri.getPort();
            Map<ServiceMatcher, String> matchers =
                mapping.computeIfAbsent(host, x -> new ConcurrentHashMap<>());
            matchers.putIfAbsent(new ServiceMatcher(port, uri.getPath()), serviceName);
          } catch (URISyntaxException use) {
            logger.warning(
                "Failed to map "
                    + key
                    + " to service "
                    + serviceName
                    + " with : "
                    + use.getMessage());
          }
        });
  }

  @Override
  public boolean isEmpty() {
    return mapping.isEmpty();
  }

  @Override
  public String resolveService(String host, Integer port, String path) {
    Map<ServiceMatcher, String> matchers = mapping.get(host);
    if (matchers == null) {
      return null;
    }
    return matchers.entrySet().stream()
        .filter(entry -> entry.getKey().matches(port, path))
        .max((o1, o2) -> matcherComparator.compare(o1.getKey(), o2.getKey()))
        .map(Map.Entry::getValue)
        .orElse(null);
  }

  private static class ServiceMatcher implements Serializable {

    private static final long serialVersionUID = 1L;
    @Nullable private final Integer port;
    @Nullable private final String path;

    ServiceMatcher(Integer port, String path) {
      this.port = port;
      this.path = path;
    }

    Integer getPort() {
      return this.port;
    }

    String getPath() {
      return this.path;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof ServiceMatcher)) {
        return false;
      }
      ServiceMatcher that = (ServiceMatcher) o;
      return Objects.equals(port, that.port) && Objects.equals(path, that.path);
    }

    @Override
    public int hashCode() {
      return Objects.hash(port, path);
    }

    public boolean matches(Integer port, String path) {
      if (this.port != null) {
        if (!this.port.equals(port)) {
          return false;
        }
      }
      if (this.path != null && this.path.length() > 0) {
        if (path == null) {
          return false;
        }
        if (!path.startsWith(this.path)) {
          return false;
        }
        if (port != null) {
          return port.equals(this.port);
        }
      }
      return true;
    }
  }
}
