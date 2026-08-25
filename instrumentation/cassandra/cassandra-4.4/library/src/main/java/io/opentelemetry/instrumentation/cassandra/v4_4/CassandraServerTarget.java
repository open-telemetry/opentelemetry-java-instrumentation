/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.cassandra.v4_4;

import com.datastax.oss.driver.api.core.context.DriverContext;
import com.datastax.oss.driver.api.core.metadata.EndPoint;
import com.datastax.oss.driver.api.core.session.Session;
import com.datastax.oss.driver.internal.core.context.InternalDriverContext;
import com.datastax.oss.driver.internal.core.metadata.DefaultNode;
import com.datastax.oss.driver.internal.core.metadata.MetadataManager;
import com.datastax.oss.driver.internal.core.metadata.SniEndPoint;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.annotation.Nullable;

/**
 * The target a session was configured with, rendered once from its contact points.
 *
 * <p>A session configured with a single contact point keeps that host and its port. A session
 * configured with several carries all valid entries in the address, in the driver's own {@code
 * host:port,host:port} syntax, and has no port of its own. Entries that do not use the driver's
 * required {@code host:port} syntax are omitted.
 *
 * <p>The driver merges {@code basic.contact-points} with contact points added on the session
 * builder. The merged set is read from the session metadata so the target includes both sources. A
 * session that has no explicit contact point keeps reporting the coordinator that answered.
 */
final class CassandraServerTarget {

  private final String address;
  @Nullable private final Integer port;

  private CassandraServerTarget(String address, @Nullable Integer port) {
    this.address = address;
    this.port = port;
  }

  /**
   * The target {@code session} was configured with, or {@code null} when it names no explicit
   * contact point or the complete target cannot be recovered.
   *
   * <p>The driver configuration can be reloaded, so read it once and keep the result, otherwise a
   * session could report two identities over its life.
   */
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
      List<CassandraServerTarget> contactPoints = new ArrayList<>();
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
        contactPoints.add(
            new CassandraServerTarget(inetAddress.getHostString(), inetAddress.getPort()));
      }
      contactPoints.sort(Comparator.comparing(CassandraServerTarget::asContactPoint));
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
    List<CassandraServerTarget> validContactPoints = new ArrayList<>();
    for (String contactPoint : contactPoints) {
      CassandraServerTarget target = single(contactPoint);
      if (target != null) {
        validContactPoints.add(target);
      }
    }
    return combine(validContactPoints);
  }

  @Nullable
  private static CassandraServerTarget combine(List<CassandraServerTarget> contactPoints) {
    if (contactPoints.isEmpty()) {
      return null;
    }
    if (contactPoints.size() == 1) {
      return contactPoints.get(0);
    }
    StringBuilder group = new StringBuilder();
    for (CassandraServerTarget contactPoint : contactPoints) {
      if (group.length() > 0) {
        group.append(',');
      }
      group.append(contactPoint.asContactPoint());
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

  /**
   * The port of a single configured contact point, or {@code null} when the target names several.
   */
  @Nullable
  Integer getPort() {
    return port;
  }
}
