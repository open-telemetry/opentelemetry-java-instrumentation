/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.service.internal;

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
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class ServiceMappingResolver {

  private static final Pattern ADDRESS_GROUP_PATTERN =
      Pattern.compile("\\(\\s*address\\s*=", Pattern.CASE_INSENSITIVE);
  private static final Logger logger = Logger.getLogger(ServiceMappingResolver.class.getName());

  // copied from ServiceIncubatingAttributes
  private static final AttributeKey<String> SERVER_SERVICE_NAME =
      AttributeKey.stringKey("server.service.name");
  private static final AttributeKey<String> SERVER_SERVICE_NAMESPACE =
      AttributeKey.stringKey("server.service.namespace");

  private static final Comparator<PortPathMatcher> matcherComparator =
      nullsFirst(
          comparing(PortPathMatcher::getPort, nullsFirst(naturalOrder()))
              .thenComparing(PortPathMatcher::getPath, nullsFirst(naturalOrder())));

  // Mappings resolved from separately supplied server.address, server.port, and optional URL path.
  private final Map<String, Map<PortPathMatcher, ServiceIdentity>> mappingsByHost = new HashMap<>();

  // Mappings resolved by matching the complete server.address value verbatim.
  private final Map<String, ServiceIdentity> mappingsByExactAddress = new HashMap<>();

  // TODO: replace OpenTelemetry parameter with ConfigProvider once it is stabilized and available
  // via openTelemetry.getConfigProvider()
  public ServiceMappingResolver(OpenTelemetry openTelemetry) {
    DeclarativeConfigUtil.getInstrumentationConfig(openTelemetry, "common")
        .getStructuredList("server_service_mapping", emptyList())
        .forEach(
            entry -> {
              String target = entry.getString("target");
              String serviceName = entry.getString("service_name");
              String serviceNamespace = entry.getString("service_namespace");
              if (target == null) {
                logger.log(
                    WARNING,
                    "Invalid server_service_mapping entry - target is required: {0}",
                    entry);
                return;
              }
              if (serviceName == null && serviceNamespace == null) {
                logger.log(
                    WARNING,
                    "Invalid server_service_mapping entry - at least one of service_name or service_namespace is required: {0}",
                    entry);
                return;
              }
              addMapping(target, serviceName, serviceNamespace);
            });
  }

  private void addMapping(
      String target, @Nullable String serviceName, @Nullable String serviceNamespace) {
    if (serviceName == null && serviceNamespace == null) {
      return;
    }
    ServiceIdentity info = new ServiceIdentity(serviceName, serviceNamespace);
    // prepend a scheme so that UrlParser can parse the host, port, and path
    String url = "https://" + target;
    String host = UrlParser.getHost(url);
    Integer port = UrlParser.getPort(url);
    String path = UrlParser.getPath(url);
    // A non-host target may be reported verbatim in server.address or as separate address, port,
    // and path components. Index single-endpoint targets both ways. A multi-endpoint target has no
    // single host identity, so only exact matching is meaningful.
    if (!target.equals(host)) {
      mappingsByExactAddress.putIfAbsent(target, info);
      if (hasMultipleEndpoints(target)) {
        return;
      }
    }
    Map<PortPathMatcher, ServiceIdentity> matchers =
        mappingsByHost.computeIfAbsent(host, x -> new HashMap<>());
    matchers.putIfAbsent(PortPathMatcher.create(port, path), info);
  }

  private static boolean hasMultipleEndpoints(String target) {
    int schemeEnd = target.indexOf("://");
    int authorityStart = schemeEnd < 0 ? 0 : schemeEnd + 3;
    int authorityEnd = target.length();
    for (int i = authorityStart; i < target.length(); i++) {
      char c = target.charAt(i);
      if (c == '/' || c == '?' || c == '#') {
        authorityEnd = i;
        break;
      }
      if (c == ',') {
        return true;
      }
    }
    Matcher addressGroups = ADDRESS_GROUP_PATTERN.matcher(target);
    addressGroups.region(authorityStart, authorityEnd);
    if (!addressGroups.find()) {
      return false;
    }
    return addressGroups.find();
  }

  public boolean isEmpty() {
    return mappingsByHost.isEmpty() && mappingsByExactAddress.isEmpty();
  }

  /**
   * Resolves an exact server.address match first, then falls back to matching server.address as a
   * host together with the separately supplied port and path.
   */
  public void resolve(
      String serverAddress,
      @Nullable Integer port,
      Supplier<String> pathSupplier,
      BiConsumer<AttributeKey<String>, String> attributeSetter) {
    ServiceIdentity serviceIdentity = resolveServiceIdentity(serverAddress, port, pathSupplier);
    if (serviceIdentity == null) {
      return;
    }

    String name = serviceIdentity.name;
    if (name != null) {
      attributeSetter.accept(SERVER_SERVICE_NAME, name);
    }
    String namespace = serviceIdentity.namespace;
    if (namespace != null) {
      attributeSetter.accept(SERVER_SERVICE_NAMESPACE, namespace);
    }
  }

  @Nullable
  private ServiceIdentity resolveServiceIdentity(
      String serverAddress, @Nullable Integer port, Supplier<String> pathSupplier) {
    ServiceIdentity exactMatch = mappingsByExactAddress.get(serverAddress);
    if (exactMatch != null) {
      return exactMatch;
    }
    Map<PortPathMatcher, ServiceIdentity> matchers = mappingsByHost.get(serverAddress);
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
  abstract static class PortPathMatcher {

    static PortPathMatcher create(@Nullable Integer port, @Nullable String path) {
      return new AutoValue_ServiceMappingResolver_PortPathMatcher(port, path);
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

  private static final class ServiceIdentity {

    @Nullable private final String name;
    @Nullable private final String namespace;

    ServiceIdentity(@Nullable String name, @Nullable String namespace) {
      this.name = name;
      this.namespace = namespace;
    }
  }
}
