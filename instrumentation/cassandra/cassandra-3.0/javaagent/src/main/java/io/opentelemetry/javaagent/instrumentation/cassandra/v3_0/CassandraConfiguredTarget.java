/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v3_0;

import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;

import com.datastax.driver.core.Cluster;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import java.lang.reflect.Array;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

public class CassandraConfiguredTarget {

  private static final int DEFAULT_PORT = 9042;

  private final String address;
  @Nullable private final Integer port;

  private CassandraConfiguredTarget(String address, @Nullable Integer port) {
    this.address = address;
    this.port = port;
  }

  public static void capture(Cluster.Builder builder, Object[] arguments) {
    ContactPoints contactPoints = VirtualFields.BUILDER_CONTACT_POINTS.get(builder);
    if (contactPoints == null) {
      contactPoints = new ContactPoints();
      VirtualFields.BUILDER_CONTACT_POINTS.set(builder, contactPoints);
    }
    contactPoints.add(arguments);
  }

  public static void store(Cluster.Builder builder, Cluster cluster, int configuredPort) {
    CassandraConfiguredTarget target =
        create(VirtualFields.BUILDER_CONTACT_POINTS.get(builder), configuredPort);
    if (target != null) {
      VirtualFields.CLUSTER_TARGET.set(cluster, target);
    }
  }

  @Nullable
  static CassandraConfiguredTarget get(Cluster cluster) {
    return VirtualFields.CLUSTER_TARGET.get(cluster);
  }

  @Nullable
  static CassandraConfiguredTarget create(Object contactPoints, int configuredPort) {
    ContactPoints captured = new ContactPoints();
    captured.add(contactPoints);
    return create(captured, configuredPort);
  }

  @Nullable
  private static CassandraConfiguredTarget create(
      @Nullable ContactPoints contactPoints, int configuredPort) {
    if (contactPoints == null || !validPort(configuredPort)) {
      return null;
    }
    if (!contactPoints.valid) {
      return null;
    }
    List<ContactPoint> points = contactPoints.points;
    if (points.isEmpty()) {
      return null;
    }

    int firstPort = resolvePort(points.get(0), configuredPort);
    boolean commonPort = true;
    List<String> hostTokens = new ArrayList<>(points.size());
    List<String> endpointTokens = new ArrayList<>(points.size());
    for (ContactPoint point : points) {
      int port = resolvePort(point, configuredPort);
      if (!validPort(port)) {
        return null;
      }
      commonPort &= port == firstPort;
      hostTokens.add(point.host);
      endpointTokens.add(formatHost(point.host) + ':' + port);
    }

    if (commonPort) {
      hostTokens.sort(String::compareTo);
      return new CassandraConfiguredTarget(
          String.join(",", hostTokens), firstPort == DEFAULT_PORT ? null : firstPort);
    }

    endpointTokens.sort(String::compareTo);
    return new CassandraConfiguredTarget(String.join(",", endpointTokens), null);
  }

  private static int resolvePort(ContactPoint point, int configuredPort) {
    return point.port == null ? configuredPort : point.port;
  }

  private static boolean validPort(int port) {
    return port > 0 && port <= 65535;
  }

  private static String formatHost(String host) {
    return host.indexOf(':') >= 0 && !host.startsWith("[") ? '[' + host + ']' : host;
  }

  @Nullable
  private static String sanitizeHost(@Nullable String host) {
    if (host == null) {
      return null;
    }
    String cleaned = host.trim();
    boolean bracketed = cleaned.startsWith("[") && cleaned.endsWith("]");
    if (bracketed) {
      cleaned = cleaned.substring(1, cleaned.length() - 1).trim();
    }
    if (cleaned.isEmpty() || cleaned.startsWith("[") || cleaned.endsWith("]")) {
      return null;
    }

    if (cleaned.indexOf(':') >= 0) {
      return isSafeIpv6Host(cleaned) ? cleaned : null;
    }
    if (bracketed) {
      return null;
    }
    for (int i = 0; i < cleaned.length(); i++) {
      char c = cleaned.charAt(i);
      if (!Character.isLetterOrDigit(c) && c != '-' && c != '.' && c != '_') {
        return null;
      }
    }
    return cleaned;
  }

  private static boolean isSafeIpv6Host(String host) {
    int zoneSeparator = host.indexOf('%');
    String address = zoneSeparator < 0 ? host : host.substring(0, zoneSeparator);
    if (address.isEmpty() || (zoneSeparator >= 0 && host.indexOf('%', zoneSeparator + 1) >= 0)) {
      return false;
    }
    try {
      new URI("http", null, address, -1, null, null, null);
    } catch (URISyntaxException ignored) {
      return false;
    }
    if (zoneSeparator < 0 || zoneSeparator == host.length() - 1) {
      return zoneSeparator < 0;
    }
    for (int i = zoneSeparator + 1; i < host.length(); i++) {
      char c = host.charAt(i);
      if (!Character.isLetterOrDigit(c) && c != '-' && c != '.' && c != '_' && c != '~') {
        return false;
      }
    }
    return true;
  }

  void put(AttributesBuilder attributes) {
    attributes.put(SERVER_ADDRESS, address);
    if (port != null) {
      attributes.put(SERVER_PORT, port);
    }
  }

  String getAddress() {
    return address;
  }

  @Nullable
  Integer getPort() {
    return port;
  }

  private static class ContactPoints {
    private final List<ContactPoint> points = new ArrayList<>();
    private boolean valid = true;

    private void add(@Nullable Object value) {
      if (value == null) {
        return;
      }
      if (value instanceof String) {
        String host = sanitizeHost((String) value);
        if (host != null) {
          points.add(new ContactPoint(host, null));
        } else if (!((String) value).isEmpty()) {
          valid = false;
        }
      } else if (value instanceof InetSocketAddress) {
        InetSocketAddress address = (InetSocketAddress) value;
        String host = sanitizeHost(address.getHostString());
        if (host != null) {
          points.add(new ContactPoint(host, address.getPort()));
        } else {
          valid = false;
        }
      } else if (value instanceof InetAddress) {
        points.add(new ContactPoint(((InetAddress) value).getHostAddress(), null));
      } else if (value instanceof Iterable) {
        for (Object element : (Iterable<?>) value) {
          add(element);
        }
      } else if (value.getClass().isArray()) {
        int length = Array.getLength(value);
        for (int i = 0; i < length; i++) {
          add(Array.get(value, i));
        }
      } else {
        valid = false;
      }
    }
  }

  private static class ContactPoint {
    private final String host;
    @Nullable private final Integer port;

    private ContactPoint(String host, @Nullable Integer port) {
      this.host = host;
      this.port = port;
    }
  }

  private static class VirtualFields {
    private static final VirtualField<Cluster.Builder, ContactPoints> BUILDER_CONTACT_POINTS =
        VirtualField.find(Cluster.Builder.class, ContactPoints.class);
    private static final VirtualField<Cluster, CassandraConfiguredTarget> CLUSTER_TARGET =
        VirtualField.find(Cluster.class, CassandraConfiguredTarget.class);
  }
}
