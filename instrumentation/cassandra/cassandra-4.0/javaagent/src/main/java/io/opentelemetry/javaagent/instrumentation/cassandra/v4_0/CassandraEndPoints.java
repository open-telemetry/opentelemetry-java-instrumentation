/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v4_0;

import com.datastax.oss.driver.api.core.metadata.EndPoint;
import java.lang.reflect.Method;
import javax.annotation.Nullable;

// Driver 4.3 added SniEndPoint, but this instrumentation also supports 4.0 to 4.2, so the class and
// its public getServerName() are reached by reflection.
class CassandraEndPoints {

  @Nullable
  private static final Class<?> SNI_END_POINT_CLASS =
      findClass("com.datastax.oss.driver.internal.core.metadata.SniEndPoint");

  @Nullable
  private static final Method SNI_GET_SERVER_NAME =
      findMethod(SNI_END_POINT_CLASS, "getServerName");

  static boolean isSniEndPoint(EndPoint endPoint) {
    return SNI_END_POINT_CLASS != null && SNI_END_POINT_CLASS.isInstance(endPoint);
  }

  @Nullable
  static String getSniServerName(EndPoint endPoint) {
    if (SNI_GET_SERVER_NAME == null) {
      return null;
    }
    try {
      return (String) SNI_GET_SERVER_NAME.invoke(endPoint);
    } catch (ReflectiveOperationException ignored) {
      return null;
    }
  }

  @Nullable
  private static Class<?> findClass(String name) {
    try {
      return Class.forName(name, false, EndPoint.class.getClassLoader());
    } catch (ClassNotFoundException ignored) {
      return null;
    }
  }

  @Nullable
  private static Method findMethod(@Nullable Class<?> type, String name) {
    if (type == null) {
      return null;
    }
    try {
      return type.getMethod(name);
    } catch (NoSuchMethodException ignored) {
      return null;
    }
  }

  private CassandraEndPoints() {}
}
