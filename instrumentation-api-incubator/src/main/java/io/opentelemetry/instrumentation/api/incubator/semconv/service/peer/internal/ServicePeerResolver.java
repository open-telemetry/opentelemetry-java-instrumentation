/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.service.peer.internal;

import static java.util.Collections.emptyList;
import static java.util.Comparator.comparing;
import static java.util.Comparator.naturalOrder;
import static java.util.Comparator.nullsFirst;
import static java.util.logging.Level.WARNING;

import com.google.auto.value.AutoValue;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.api.incubator.semconv.net.internal.UrlParser;
import io.opentelemetry.instrumentation.api.internal.SemconvStability;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class ServicePeerResolver {

  private static final Logger logger = Logger.getLogger(ServicePeerResolver.class.getName());

  // copied from PeerIncubatingAttributes
  private static final AttributeKey<String> PEER_SERVICE = AttributeKey.stringKey("peer.service");
  // copied from ServiceIncubatingAttributes
  private static final AttributeKey<String> SERVICE_PEER_NAME =
      AttributeKey.stringKey("service.peer.name");
  private static final AttributeKey<String> SERVICE_PEER_NAMESPACE =
      AttributeKey.stringKey("service.peer.namespace");

  private static final Comparator<ServiceMatcher> matcherComparator =
      nullsFirst(
          comparing(ServiceMatcher::getPort, nullsFirst(naturalOrder()))
              .thenComparing(ServiceMatcher::getPath, nullsFirst(naturalOrder())));

  private final Map<String, Map<ServiceMatcher, ServicePeer>> servicePeerMapping = new HashMap<>();

  public ServicePeerResolver(OpenTelemetry openTelemetry) {
    DeclarativeConfigUtil.getInstrumentationConfig(openTelemetry, "common")
        .getStructuredList("service_peer_mapping", emptyList())
        .forEach(
            entry -> {
              String peer = entry.getString("peer");
              String serviceName = entry.getString("service_name");
              String serviceNamespace = entry.getString("service_namespace");
              if (peer == null) {
                logger.log(
                    WARNING, "Invalid service_peer_mapping entry - peer is required: {0}", entry);
                return;
              }
              if (serviceName == null && serviceNamespace == null) {
                logger.log(
                    WARNING,
                    "Invalid service_peer_mapping entry - at least one of service_name or service_namespace is required: {0}",
                    entry);
                return;
              }
              addMapping(peer, serviceName, serviceNamespace);
            });
  }

  @SuppressWarnings("deprecation") // used by deprecated PeerServiceResolver
  public ServicePeerResolver(Map<String, String> servicePeerNameMapping) {
    servicePeerNameMapping.forEach((peer, serviceName) -> addMapping(peer, serviceName, null));
  }

  private void addMapping(
      String peer, @Nullable String serviceName, @Nullable String serviceNamespace) {
    if (serviceName == null && serviceNamespace == null) {
      return;
    }
    ServicePeer info = new ServicePeer(serviceName, serviceNamespace);
    // prepend a scheme so that UrlParser can parse the host, port, and path
    String url = "https://" + peer;
    String host = UrlParser.getHost(url);
    Integer port = UrlParser.getPort(url);
    String path = UrlParser.getPath(url);
    Map<ServiceMatcher, ServicePeer> matchers =
        servicePeerMapping.computeIfAbsent(host, x -> new HashMap<>());
    matchers.putIfAbsent(ServiceMatcher.create(port, path), info);
  }

  public boolean isEmpty() {
    return servicePeerMapping.isEmpty();
  }

  @SuppressWarnings("deprecation") // old semconv
  public void resolve(
      String host,
      @Nullable Integer port,
      Supplier<String> pathSupplier,
      BiConsumer<AttributeKey<String>, String> attributeSetter) {
    ServicePeer servicePeer = resolveServicePeer(host, port, pathSupplier);
    if (servicePeer == null) {
      return;
    }

    String name = servicePeer.name;
    if (name != null) {
      if (SemconvStability.emitOldServicePeerSemconv()) {
        attributeSetter.accept(PEER_SERVICE, name);
      }
      if (SemconvStability.emitStableServicePeerSemconv()) {
        attributeSetter.accept(SERVICE_PEER_NAME, name);
      }
    }
    if (SemconvStability.emitStableServicePeerSemconv()) {
      String namespace = servicePeer.namespace;
      if (namespace != null) {
        attributeSetter.accept(SERVICE_PEER_NAMESPACE, namespace);
      }
    }
  }

  // TODO: remove this method when deprecated PeerServiceResolver is removed
  @Nullable
  public String resolveServiceName(
      String host, @Nullable Integer port, Supplier<String> pathSupplier) {
    ServicePeer peer = resolveServicePeer(host, port, pathSupplier);
    return peer != null ? peer.name : null;
  }

  @Nullable
  ServicePeer resolveServicePeer(
      String host, @Nullable Integer port, Supplier<String> pathSupplier) {
    Map<ServiceMatcher, ServicePeer> matchers = servicePeerMapping.get(host);
    if (matchers == null) {
      return null;
    }
    return matchers.entrySet().stream()
        .filter(entry -> entry.getKey().matches(port, pathSupplier))
        .max((o1, o2) -> matcherComparator.compare(o1.getKey(), o2.getKey()))
        .map(Map.Entry::getValue)
        .orElse(null);
  }

  // TODO: remove this method when deprecated PeerServiceResolver is removed
  @SuppressWarnings("deprecation") // bridges deprecated PeerServiceResolver
  public static ServicePeerResolver fromPeerServiceResolver(
      io.opentelemetry.instrumentation.api.incubator.semconv.net.PeerServiceResolver resolver) {
    return new ServicePeerResolver(new HashMap<>()) {
      @Override
      public boolean isEmpty() {
        return resolver.isEmpty();
      }

      @Override
      @Nullable
      ServicePeer resolveServicePeer(
          String host, @Nullable Integer port, Supplier<String> pathSupplier) {
        String serviceName = resolver.resolveService(host, port, pathSupplier);
        return serviceName != null ? new ServicePeer(serviceName, null) : null;
      }
    };
  }

  @AutoValue
  abstract static class ServiceMatcher {

    static ServiceMatcher create(@Nullable Integer port, @Nullable String path) {
      return new AutoValue_ServicePeerResolver_ServiceMatcher(port, path);
    }

    @Nullable
    abstract Integer getPort();

    @Nullable
    abstract String getPath();

    public boolean matches(@Nullable Integer port, Supplier<String> pathSupplier) {
      if (this.getPort() != null) {
        if (!this.getPort().equals(port)) {
          return false;
        }
      }
      if (this.getPath() != null && this.getPath().length() > 0) {
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

  private static final class ServicePeer {

    @Nullable private final String name;
    @Nullable private final String namespace;

    ServicePeer(@Nullable String name, @Nullable String namespace) {
      this.name = name;
      this.namespace = namespace;
    }
  }
}
