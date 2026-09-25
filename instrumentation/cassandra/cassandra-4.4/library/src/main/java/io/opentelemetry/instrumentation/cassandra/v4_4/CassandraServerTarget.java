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
import com.datastax.oss.driver.internal.core.metadata.DefaultEndPoint;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTarget;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTargetBuilder;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;

final class CassandraServerTarget {

  private static final int DEFAULT_PORT = 9042;

  @Nullable
  static DbServerTarget of(Session session, Set<EndPoint> programmaticContactPoints) {
    try {
      DriverContext context = session.getContext();
      DriverExecutionProfile config = context.getConfig().getDefaultProfile();
      // The basic.contact-points option has no default, so the single-argument lookup would throw
      // when a session names its contact points on the builder alone.
      List<String> configuredContactPoints = config.getStringList(CONTACT_POINTS, emptyList());
      DbServerTargetBuilder target = DbServerTarget.builder(DEFAULT_PORT).setSorted(true);
      for (String contactPoint : configuredContactPoints) {
        addContactPoint(target, contactPoint);
      }
      for (EndPoint endPoint : programmaticContactPoints) {
        if (endPoint.getClass() != DefaultEndPoint.class) {
          return null;
        }
        SocketAddress address = endPoint.resolve();
        if (!(address instanceof InetSocketAddress)) {
          return null;
        }
        target.addEndpoint((InetSocketAddress) address);
      }
      return target.build();
    } catch (RuntimeException ignored) {
      // A session that cannot describe its configuration has no stable server target.
      return null;
    }
  }

  @Nullable
  static DbServerTarget of(@Nullable List<String> contactPoints) {
    if (contactPoints == null || contactPoints.isEmpty()) {
      return null;
    }
    DbServerTargetBuilder target = DbServerTarget.builder(DEFAULT_PORT).setSorted(true);
    for (String contactPoint : contactPoints) {
      addContactPoint(target, contactPoint);
    }
    return target.build();
  }

  @Nullable
  static DbServerTarget ofAddresses(Collection<InetSocketAddress> contactPoints) {
    try {
      DbServerTargetBuilder target = DbServerTarget.builder(DEFAULT_PORT).setSorted(true);
      for (InetSocketAddress contactPoint : contactPoints) {
        target.addEndpoint(contactPoint);
      }
      return target.build();
    } catch (RuntimeException ignored) {
      // Unsafe or malformed contact points have no stable server target.
      return null;
    }
  }

  private static void addContactPoint(DbServerTargetBuilder target, @Nullable String contactPoint) {
    if (contactPoint == null) {
      target.addEndpoint((String) null, -1);
      return;
    }
    int separator = contactPoint.lastIndexOf(':');
    if (separator < 0) {
      target.addEndpoint((String) null, -1);
      return;
    }
    String host = contactPoint.substring(0, separator);
    if (host.startsWith("[")) {
      if (!host.endsWith("]") || host.indexOf(':') < 0) {
        target.addEndpoint((String) null, -1);
        return;
      }
      host = host.substring(1, host.length() - 1);
    } else if (host.indexOf(':') >= 0) {
      target.addEndpoint((String) null, -1);
      return;
    }
    try {
      int port = Integer.parseInt(contactPoint.substring(separator + 1));
      target.addEndpoint(host, port);
    } catch (NumberFormatException ignored) {
      target.addEndpoint((String) null, -1);
    }
  }

  private CassandraServerTarget() {}
}
