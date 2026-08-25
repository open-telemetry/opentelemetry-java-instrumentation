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
 * configured with several carries all of them in the address, in the driver's own {@code
 * host:port,host:port} syntax, and has no port of its own. Contact points carry no credentials,
 * path or options, so the configured text is already the target.
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
    if (contactPoints.size() == 1) {
      return single(contactPoints.get(0).trim());
    }
    StringBuilder group = new StringBuilder();
    for (String contactPoint : contactPoints) {
      String trimmed = contactPoint.trim();
      if (trimmed.isEmpty()) {
        return null;
      }
      if (group.length() > 0) {
        group.append(',');
      }
      group.append(bracketIpv6(trimmed));
    }
    return new CassandraServerTarget(group.toString(), null);
  }

  @Nullable
  private static CassandraServerTarget single(String contactPoint) {
    if (contactPoint.isEmpty()) {
      return null;
    }
    if (contactPoint.startsWith("[")) {
      int end = contactPoint.indexOf(']');
      if (end < 0) {
        return null;
      }
      String host = contactPoint.substring(1, end);
      String rest = contactPoint.substring(end + 1);
      return host.isEmpty()
          ? null
          : new CassandraServerTarget(host, rest.startsWith(":") ? port(rest.substring(1)) : null);
    }
    int separator = contactPoint.indexOf(':');
    if (separator < 0 || contactPoint.lastIndexOf(':') != separator) {
      // no port at all, or a bare ipv6 address, which carries no port either
      return new CassandraServerTarget(contactPoint, null);
    }
    String host = contactPoint.substring(0, separator);
    return host.isEmpty()
        ? null
        : new CassandraServerTarget(host, port(contactPoint.substring(separator + 1)));
  }

  @Nullable
  private static Integer port(String port) {
    try {
      return Integer.valueOf(port);
    } catch (NumberFormatException ignored) {
      return null;
    }
  }

  // a literal ipv6 address is bracketed so that the port stays unambiguous
  private static String bracketIpv6(String contactPoint) {
    if (contactPoint.startsWith("[")
        || contactPoint.indexOf(':') == contactPoint.lastIndexOf(':')) {
      return contactPoint;
    }
    return '[' + contactPoint + ']';
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
