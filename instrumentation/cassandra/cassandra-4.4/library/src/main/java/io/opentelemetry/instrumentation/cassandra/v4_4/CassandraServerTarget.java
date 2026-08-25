/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.cassandra.v4_4;

import com.datastax.oss.driver.api.core.config.DefaultDriverOption;
import com.datastax.oss.driver.api.core.config.DriverExecutionProfile;
import com.datastax.oss.driver.api.core.session.Session;
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
 * <p>Only the contact points in {@code basic.contact-points} are read. They are the sole place the
 * driver keeps what an operator configured; contact points added on the session builder are held in
 * a field of that builder, which a built session does not expose. A session that has none keeps
 * reporting the coordinator that answered.
 */
final class CassandraServerTarget {

  private final String address;
  @Nullable private final Integer port;

  private CassandraServerTarget(String address, @Nullable Integer port) {
    this.address = address;
    this.port = port;
  }

  /**
   * The target {@code session} was configured with, or {@code null} when it names no contact point.
   *
   * <p>The driver configuration can be reloaded, so read it once and keep the result, otherwise a
   * session could report two identities over its life.
   */
  @Nullable
  static CassandraServerTarget of(Session session) {
    try {
      DriverExecutionProfile profile = session.getContext().getConfig().getDefaultProfile();
      if (!profile.isDefined(DefaultDriverOption.CONTACT_POINTS)) {
        return null;
      }
      return of(profile.getStringList(DefaultDriverOption.CONTACT_POINTS));
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
    CassandraServerTarget first = null;
    int validCount = 0;
    StringBuilder group = new StringBuilder();
    for (String contactPoint : contactPoints) {
      CassandraServerTarget target = single(contactPoint);
      if (target == null) {
        continue;
      }
      if (first == null) {
        first = target;
      }
      validCount++;
      if (group.length() > 0) {
        group.append(',');
      }
      group.append(target.asContactPoint());
    }
    if (first == null) {
      return null;
    }
    return validCount == 1 ? first : new CassandraServerTarget(group.toString(), null);
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
