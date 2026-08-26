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
import com.datastax.oss.driver.internal.core.metadata.DefaultNode;
import com.datastax.oss.driver.internal.core.metadata.MetadataManager;
import com.datastax.oss.driver.internal.core.metadata.SniEndPoint;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import javax.annotation.Nullable;

final class CassandraServerTarget {

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
      boolean hasConfiguredTargets = !contactPoints.isEmpty();
      for (DefaultNode node : metadataManager.getContactPoints()) {
        EndPoint endPoint = node.getEndPoint();
        if (endPoint instanceof SniEndPoint) {
          return null;
        }
        SocketAddress address = endPoint.resolve();
        if (!(address instanceof InetSocketAddress)) {
          return null;
        }
        InetSocketAddress inetAddress = (InetSocketAddress) address;
        CassandraServerTarget target =
            new CassandraServerTarget(inetAddress.getHostString(), inetAddress.getPort());
        if (matches(contactPoints, target)) {
          continue;
        }
        if (hasConfiguredTargets) {
          return null;
        }
        contactPoints.add(target);
      }
      return combine(contactPoints);
    } catch (RuntimeException ignored) {
      // a session that cannot describe its own configuration keeps reporting its coordinator
      return null;
    }
  }

  @Nullable
  static CassandraServerTarget of(
      @Nullable List<String> configuredContactPoints,
      @Nullable Set<EndPoint> programmaticContactPoints) {
    if (configuredContactPoints == null || programmaticContactPoints == null) {
      return null;
    }
    try {
      List<CassandraServerTarget> contactPoints = valid(configuredContactPoints);
      for (EndPoint endPoint : programmaticContactPoints) {
        if (endPoint instanceof SniEndPoint) {
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
      // a session that cannot describe its own configuration keeps reporting its coordinator
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

  private static boolean matches(
      List<CassandraServerTarget> configuredTargets, CassandraServerTarget target) {
    for (CassandraServerTarget configuredTarget : configuredTargets) {
      if (!configuredTarget.port.equals(target.port)) {
        continue;
      }
      if (configuredTarget.address.equals(target.address)) {
        return true;
      }
      if (configuredTarget.address.indexOf(':') < 0 || target.address.indexOf(':') < 0) {
        continue;
      }
      try {
        // The driver may expand a configured IPv6 literal when it builds the metadata endpoint.
        InetAddress configuredAddress = InetAddress.getByName(configuredTarget.address);
        InetAddress targetAddress = InetAddress.getByName(target.address);
        if (configuredAddress.equals(targetAddress)) {
          return true;
        }
      } catch (UnknownHostException ignored) {
        // Invalid configured contact points do not match retained metadata endpoints.
      }
    }
    return false;
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
