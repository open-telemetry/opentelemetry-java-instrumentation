/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v4_0;

import static com.datastax.oss.driver.api.core.config.DefaultDriverOption.CONTACT_POINTS;
import static io.opentelemetry.javaagent.instrumentation.cassandra.v4_0.CassandraEndPoints.isDefaultEndPoint;
import static java.util.Collections.emptyList;

import com.datastax.oss.driver.api.core.config.DriverExecutionProfile;
import com.datastax.oss.driver.api.core.context.DriverContext;
import com.datastax.oss.driver.api.core.metadata.EndPoint;
import com.datastax.oss.driver.api.core.session.Session;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;

class CassandraServerTarget {

  private static final int DEFAULT_PORT = 9042;
  private static final int MAX_ADDRESS_LENGTH = 255;

  private final String address;
  @Nullable private final Integer port;

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
        if (!isDefaultEndPoint(endPoint)) {
          return null;
        }
        SocketAddress address = endPoint.resolve();
        if (!(address instanceof InetSocketAddress)) {
          return null;
        }
        InetSocketAddress inetAddress = (InetSocketAddress) address;
        if (!isSafeHost(inetAddress.getHostString()) || !validPort(inetAddress.getPort())) {
          return null;
        }
        contactPoints.add(
            new CassandraServerTarget(inetAddress.getHostString(), inetAddress.getPort()));
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

    int commonPort = contactPoints.get(0).port;
    boolean allPortsEqual = true;
    List<String> hosts = new ArrayList<>(contactPoints.size());
    List<String> endpoints = new ArrayList<>(contactPoints.size());
    for (CassandraServerTarget contactPoint : contactPoints) {
      allPortsEqual &= contactPoint.port == commonPort;
      hosts.add(contactPoint.address);
      endpoints.add(contactPoint.asContactPoint());
    }

    if (allPortsEqual) {
      hosts.sort(String::compareTo);
      String address = joinWithinLimit(hosts);
      return address == null
          ? null
          : new CassandraServerTarget(address, commonPort == DEFAULT_PORT ? null : commonPort);
    }

    endpoints.sort(String::compareTo);
    String address = joinWithinLimit(endpoints);
    return address == null ? null : new CassandraServerTarget(address, null);
  }

  @Nullable
  private static String joinWithinLimit(List<String> values) {
    StringBuilder result = new StringBuilder();
    for (String value : values) {
      int separatorLength = result.length() == 0 ? 0 : 1;
      if (value.length() > MAX_ADDRESS_LENGTH - result.length() - separatorLength) {
        break;
      }
      if (separatorLength != 0) {
        result.append(',');
      }
      result.append(value);
    }
    return result.length() == 0 ? null : result.toString();
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
    Integer port = port(contactPoint.substring(separator + 1));
    return isSafeHost(host) && port != null ? new CassandraServerTarget(host, port) : null;
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
