/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v4_0;

import static com.datastax.oss.driver.api.core.config.DefaultDriverOption.CONTACT_POINTS;
import static java.util.Collections.emptyList;

import com.datastax.oss.driver.api.core.config.DriverExecutionProfile;
import com.datastax.oss.driver.api.core.context.DriverContext;
import com.datastax.oss.driver.api.core.metadata.EndPoint;
import com.datastax.oss.driver.api.core.session.Session;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import javax.annotation.Nullable;

class CassandraServerTarget {

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
      for (EndPoint endPoint : programmaticContactPoints) {
        if (CassandraEndPoints.isSniEndPoint(endPoint)) {
          return null;
        }
        SocketAddress address = endPoint.resolve();
        if (!(address instanceof InetSocketAddress)) {
          return null;
        }
        InetSocketAddress inetAddress = (InetSocketAddress) address;
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
    return combine(valid(contactPoints));
  }

  private CassandraServerTarget(String address, @Nullable Integer port) {
    this.address = address;
    this.port = port;
  }

  private static List<CassandraServerTarget> valid(List<String> contactPoints) {
    List<CassandraServerTarget> validContactPoints = new ArrayList<>();
    for (String contactPoint : contactPoints) {
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
    Map<String, CassandraServerTarget> uniqueContactPoints = new TreeMap<>();
    for (CassandraServerTarget contactPoint : contactPoints) {
      uniqueContactPoints.put(contactPoint.asContactPoint(), contactPoint);
    }
    if (uniqueContactPoints.size() == 1) {
      return uniqueContactPoints.values().iterator().next();
    }
    StringBuilder group = new StringBuilder();
    for (String contactPoint : uniqueContactPoints.keySet()) {
      if (group.length() > 0) {
        group.append(',');
      }
      group.append(contactPoint);
    }
    return new CassandraServerTarget(group.toString(), null);
  }

  @Nullable
  private static CassandraServerTarget single(String contactPoint) {
    int separator = contactPoint.lastIndexOf(':');
    if (separator < 0) {
      return null;
    }
    String host = contactPoint.substring(0, separator);
    if (host.startsWith("[") && host.endsWith("]")) {
      host = host.substring(1, host.length() - 1);
    }
    Integer port = port(contactPoint.substring(separator + 1));
    return host.isEmpty() || port == null ? null : new CassandraServerTarget(host, port);
  }

  @Nullable
  private static Integer port(String port) {
    try {
      int value = Integer.parseInt(port);
      return value >= 0 && value <= 65535 ? value : null;
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
