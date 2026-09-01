/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v4_0;

import com.datastax.oss.driver.api.core.metadata.EndPoint;
import javax.annotation.Nullable;

// Driver 4.3 added SniEndPoint, but this instrumentation also supports 4.0 to 4.2.
class CassandraEndPoints {

  private static final String DEFAULT_END_POINT_CLASS_NAME =
      "com.datastax.oss.driver.internal.core.metadata.DefaultEndPoint";

  @Nullable
  private static final Class<?> SNI_END_POINT_CLASS =
      findClass("com.datastax.oss.driver.internal.core.metadata.SniEndPoint");

  static boolean isDefaultEndPoint(EndPoint endPoint) {
    return endPoint.getClass().getName().equals(DEFAULT_END_POINT_CLASS_NAME);
  }

  static boolean isSniEndPoint(EndPoint endPoint) {
    return SNI_END_POINT_CLASS != null && SNI_END_POINT_CLASS.isInstance(endPoint);
  }

  @Nullable
  private static Class<?> findClass(String name) {
    try {
      return Class.forName(name, false, EndPoint.class.getClassLoader());
    } catch (ClassNotFoundException ignored) {
      return null;
    }
  }

  private CassandraEndPoints() {}
}
