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
      targetBuilder.addEndpoint(point.host, point.port == null ? configuredPort : point.port);
    }
    return targetBuilder.build();
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
