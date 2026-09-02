/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.cassandra.v4_4;

import static com.datastax.oss.driver.api.core.config.DefaultDriverOption.CONTACT_POINTS;
import static java.util.Collections.emptyList;

import com.datastax.oss.driver.api.core.config.DriverExecutionProfile;
import com.datastax.oss.driver.api.core.context.DriverContext;
import com.datastax.oss.driver.api.core.metadata.EndPoint;
import com.datastax.oss.driver.api.core.session.Session;
import com.datastax.oss.driver.internal.core.context.InternalDriverContext;
import com.datastax.oss.driver.internal.core.metadata.DefaultEndPoint;
import com.datastax.oss.driver.internal.core.metadata.DefaultNode;
import com.datastax.oss.driver.internal.core.metadata.MetadataManager;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;

final class CassandraServerTarget {

  private static final int DEFAULT_PORT = 9042;
  private static final int MAX_ENDPOINTS = 5;

  private final String address;
  @Nullable private final Integer port;

  @Nullable
  static CassandraServerTarget of(Session session) {
    try {
      DriverContext context = session.getContext();
      if (!(context instanceof InternalDriverContext)) {
        return null;
      }
      MetadataManager metadataManager = ((InternalDriverContext) context).getMetadataManager();
      if (metadataManager.wasImplicitContactPoint()) {
        return null;
      }
      DriverExecutionProfile config = context.getConfig().getDefaultProfile();
      // basic.contact-points has no default, so the single argument lookup would throw when a
      // session names its contact points on the builder alone
      List<String> configuredContactPoints = config.getStringList(CONTACT_POINTS, emptyList());
      List<CassandraServerTarget> contactPoints = valid(configuredContactPoints);
      if (contactPoints == null) {
        return null;
      }
      boolean hasConfiguredContactPoints = !configuredContactPoints.isEmpty();
      for (DefaultNode node : metadataManager.getContactPoints()) {
        EndPoint endPoint = node.getEndPoint();
        if (endPoint.getClass() != DefaultEndPoint.class) {
          return null;
        }
        SocketAddress address = endPoint.resolve();
        if (!(address instanceof InetSocketAddress)) {
          return null;
        }
        CassandraServerTarget target = fromAddress((InetSocketAddress) address);
        if (target == null) {
          return null;
        }
        if (matches(contactPoints, target)) {
          continue;
        }
        if (hasConfiguredContactPoints) {
          return null;
        }
        contactPoints.add(target);
      }
      return combine(contactPoints);
    } catch (RuntimeException ignored) {
      // A session that cannot describe its configuration has no stable server target.
      return null;
    }
  }

  @Nullable
  static CassandraServerTarget of(Session session, Set<EndPoint> programmaticContactPoints) {
    try {
      DriverContext context = session.getContext();
      DriverExecutionProfile config = context.getConfig().getDefaultProfile();
      // basic.contact-points has no default, so the single argument lookup would throw when a
      // session names its contact points on the builder alone
      List<String> configuredContactPoints = config.getStringList(CONTACT_POINTS, emptyList());
      List<CassandraServerTarget> contactPoints = valid(configuredContactPoints);
      if (contactPoints == null) {
        return null;
      }
      for (EndPoint endPoint : programmaticContactPoints) {
        if (endPoint.getClass() != DefaultEndPoint.class) {
          return null;
        }
        SocketAddress address = endPoint.resolve();
        if (!(address instanceof InetSocketAddress)) {
          return null;
        }
        CassandraServerTarget target = fromAddress((InetSocketAddress) address);
        if (target == null) {
          return null;
        }
        contactPoints.add(target);
      }
      return combine(contactPoints);
    } catch (RuntimeException ignored) {
      // A session that cannot describe its configuration has no stable server target.
      return null;
    }
  }

  @Nullable
  static CassandraServerTarget of(@Nullable List<String> contactPoints) {
    if (contactPoints == null || contactPoints.isEmpty()) {
      return null;
    }
    List<CassandraServerTarget> validContactPoints = valid(contactPoints);
    return validContactPoints == null ? null : combine(validContactPoints);
  }

  @Nullable
  static CassandraServerTarget ofAddresses(Collection<InetSocketAddress> contactPoints) {
    List<CassandraServerTarget> targets = new ArrayList<>();
    for (InetSocketAddress contactPoint : contactPoints) {
      if (contactPoint == null) {
        return null;
      }
      CassandraServerTarget target = fromAddress(contactPoint);
      if (target == null) {
        return null;
      }
      targets.add(target);
    }
    return combine(targets);
  }

  private CassandraServerTarget(String address, @Nullable Integer port) {
    this.address = address;
    this.port = port;
  }

  @Nullable
  private static List<CassandraServerTarget> valid(List<String> contactPoints) {
    List<CassandraServerTarget> validContactPoints = new ArrayList<>();
    for (String contactPoint : contactPoints) {
      if (contactPoint != null && !isSafeHost(contactPoint)) {
        return null;
      }
      CassandraServerTarget target = single(contactPoint);
      if (target != null) {
        validContactPoints.add(target);
      }
    }
    return validContactPoints;
  }

  @Nullable
  private static CassandraServerTarget combine(List<CassandraServerTarget> contactPoints) {
    if (contactPoints.isEmpty()) {
      return null;
    }
    if (contactPoints.size() == 1) {
      CassandraServerTarget contactPoint = contactPoints.get(0);
      return new CassandraServerTarget(
          contactPoint.address, contactPoint.port == DEFAULT_PORT ? null : contactPoint.port);
    }

    boolean allPortsDefault = true;
    List<String> hosts = new ArrayList<>(contactPoints.size());
    List<String> endpoints = new ArrayList<>(contactPoints.size());
    for (CassandraServerTarget contactPoint : contactPoints) {
      allPortsDefault &= contactPoint.port == DEFAULT_PORT;
      hosts.add(contactPoint.address);
      endpoints.add(contactPoint.asContactPoint());
    }

    List<String> renderedEndpoints = allPortsDefault ? hosts : endpoints;
    renderedEndpoints.sort(String::compareTo);
    return new CassandraServerTarget(joinFirstEndpoints(renderedEndpoints), null);
  }

  private static String joinFirstEndpoints(List<String> endpoints) {
    return String.join(",", endpoints.subList(0, Math.min(endpoints.size(), MAX_ENDPOINTS)));
  }

  private static boolean matches(
      List<CassandraServerTarget> configuredTargets, CassandraServerTarget target) {
    for (CassandraServerTarget configuredTarget : configuredTargets) {
      if (!configuredTarget.port.equals(target.port)) {
        continue;
      }
      if (configuredTarget.address.equals(target.address)) {
        return true;
      }
      InetAddress configuredAddress = numericAddress(configuredTarget.address);
      InetAddress targetAddress = numericAddress(target.address);
      if (configuredAddress != null && configuredAddress.equals(targetAddress)) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  private static InetAddress numericAddress(String address) {
    if (address.indexOf(':') >= 0) {
      try {
        return InetAddress.getByName(address);
      } catch (UnknownHostException ignored) {
        return null;
      }
    }

    byte[] bytes = ipv4Address(address);
    if (bytes == null) {
      return null;
    }
    try {
      return InetAddress.getByAddress(bytes);
    } catch (UnknownHostException ignored) {
      return null;
    }
  }

  @Nullable
  private static byte[] ipv4Address(String address) {
    byte[] bytes = new byte[4];
    int byteIndex = 0;
    int value = 0;
    int digits = 0;
    for (int i = 0; i <= address.length(); i++) {
      char c = i == address.length() ? '.' : address.charAt(i);
      if (c == '.') {
        if (digits == 0 || byteIndex == bytes.length) {
          return null;
        }
        bytes[byteIndex++] = (byte) value;
        value = 0;
        digits = 0;
      } else if (c >= '0' && c <= '9') {
        value = value * 10 + c - '0';
        digits++;
        if (digits > 3 || value > 255) {
          return null;
        }
      } else {
        return null;
      }
    }
    return byteIndex == bytes.length ? bytes : null;
  }

  @Nullable
  private static CassandraServerTarget single(@Nullable String contactPoint) {
    if (contactPoint == null) {
      return null;
    }
    int separator = contactPoint.lastIndexOf(':');
    if (separator < 0) {
      return null;
    }
    String host = contactPoint.substring(0, separator);
    if (host.startsWith("[")) {
      if (!host.endsWith("]")) {
        return null;
      }
      host = host.substring(1, host.length() - 1);
    } else if (host.indexOf('[') >= 0 || host.indexOf(']') >= 0) {
      return null;
    }
    if (!isValidHost(host)) {
      return null;
    }
    Integer port = port(contactPoint.substring(separator + 1));
    return port == null ? null : new CassandraServerTarget(host, port);
  }

  @Nullable
  private static CassandraServerTarget fromAddress(InetSocketAddress address) {
    String host = address.getHostString();
    if (host.startsWith("[")) {
      if (!host.endsWith("]")) {
        return null;
      }
      host = host.substring(1, host.length() - 1);
    } else if (host.indexOf('[') >= 0 || host.indexOf(']') >= 0) {
      return null;
    }
    return isSafeHost(host) && validPort(address.getPort())
        ? new CassandraServerTarget(host, address.getPort())
        : null;
  }

  private static boolean isSafeHost(String host) {
    if (host.isEmpty()) {
      return false;
    }
    for (int i = 0; i < host.length(); i++) {
      char c = host.charAt(i);
      if (c <= ' ' || c == '@' || c == '/' || c == '\\' || c == '?' || c == '#' || c == ',') {
        return false;
      }
    }
    return true;
  }

  private static boolean isValidHost(String host) {
    if (!isSafeHost(host)) {
      return false;
    }
    if (host.indexOf(':') < 0) {
      return true;
    }
    try {
      new URI("cassandra", null, host, DEFAULT_PORT, null, null, null);
      return true;
    } catch (URISyntaxException ignored) {
      return false;
    }
  }

  private static boolean validPort(int port) {
    return port > 0 && port <= 65535;
  }

  @Nullable
  private static Integer port(String port) {
    try {
      int value = Integer.parseInt(port);
      return validPort(value) ? value : null;
    } catch (NumberFormatException ignored) {
      return null;
    }
  }

  private String asContactPoint() {
    String host = address.indexOf(':') < 0 ? address : '[' + address + ']';
    return host + ':' + port;
  }

  String getAddress() {
    return address;
  }

  @Nullable
  Integer getPort() {
    return port;
  }
}
