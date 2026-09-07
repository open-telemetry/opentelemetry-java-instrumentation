/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v3_0;

import com.datastax.driver.core.Cluster;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTarget;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTargetBuilder;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import java.lang.reflect.Array;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

public class CassandraServerTarget {

  private static final int DEFAULT_PORT = 9042;

  private static final VirtualField<Cluster.Builder, ContactPoints> BUILDER_CONTACT_POINTS =
      VirtualField.find(Cluster.Builder.class, ContactPoints.class);
  private static final VirtualField<Cluster, DbServerTarget> CLUSTER_TARGET =
      VirtualField.find(Cluster.class, DbServerTarget.class);

  public static void capture(Cluster.Builder builder, Object[] arguments) {
    getOrCreateContactPoints(builder).add(arguments);
  }

  public static void invalidate(Cluster.Builder builder) {
    getOrCreateContactPoints(builder).valid = false;
  }

  private static ContactPoints getOrCreateContactPoints(Cluster.Builder builder) {
    ContactPoints contactPoints = BUILDER_CONTACT_POINTS.get(builder);
    if (contactPoints == null) {
      contactPoints = new ContactPoints();
      BUILDER_CONTACT_POINTS.set(builder, contactPoints);
    }
    return contactPoints;
  }

  public static void store(Cluster.Builder builder, Cluster cluster, int configuredPort) {
    DbServerTarget target = create(BUILDER_CONTACT_POINTS.get(builder), configuredPort);
    if (target != null) {
      CLUSTER_TARGET.set(cluster, target);
    }
  }

  @Nullable
  static DbServerTarget get(Cluster cluster) {
    return CLUSTER_TARGET.get(cluster);
  }

  @Nullable
  static DbServerTarget create(Object contactPoints, int configuredPort) {
    ContactPoints captured = new ContactPoints();
    captured.add(contactPoints);
    return create(captured, configuredPort);
  }

  @Nullable
  private static DbServerTarget create(@Nullable ContactPoints contactPoints, int configuredPort) {
    if (contactPoints == null || !contactPoints.valid) {
      return null;
    }

    DbServerTargetBuilder targetBuilder = DbServerTarget.builder(DEFAULT_PORT);
    for (ContactPoint point : contactPoints.points) {
      // TODO(#20016): Switch to DbServerEndpointUtil after #20015 merges.
      if (!isSafeEndpointHost(point.host)) {
        return null;
      }
      targetBuilder.addEndpoint(point.host, point.port == null ? configuredPort : point.port);
    }
    return targetBuilder.build();
  }

  private static boolean isSafeEndpointHost(String host) {
    String sanitized = host.trim();
    boolean bracketed =
        sanitized.length() >= 2
            && sanitized.charAt(0) == '['
            && sanitized.charAt(sanitized.length() - 1) == ']';
    if (bracketed) {
      sanitized = sanitized.substring(1, sanitized.length() - 1).trim();
    }
    if (sanitized.indexOf(':') >= 0) {
      return CassandraDbServerEndpointUtil.isIpv6Literal(sanitized);
    }
    if (bracketed || !looksLikeIpv4Literal(sanitized)) {
      return true;
    }
    return CassandraDbServerEndpointUtil.isIpv4Literal(sanitized);
  }

  private static boolean looksLikeIpv4Literal(String host) {
    for (int i = 0; i < host.length(); i++) {
      char c = host.charAt(i);
      if (c != '.' && !isAsciiDigit(c)) {
        return false;
      }
    }
    return host.indexOf('.') >= 0;
  }

  private static boolean isAsciiDigit(char c) {
    return c >= '0' && c <= '9';
  }

  private CassandraServerTarget() {}

  private static class ContactPoints {
    private final List<ContactPoint> points = new ArrayList<>();
    private boolean valid = true;

    private void add(@Nullable Object value) {
      if (value == null) {
        valid = false;
        return;
      }
      if (value instanceof String) {
        points.add(new ContactPoint((String) value, null));
      } else if (value instanceof InetSocketAddress) {
        InetSocketAddress address = (InetSocketAddress) value;
        points.add(new ContactPoint(address.getHostString(), address.getPort()));
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
}
