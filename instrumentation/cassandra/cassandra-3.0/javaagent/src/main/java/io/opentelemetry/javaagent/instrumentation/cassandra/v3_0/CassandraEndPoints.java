/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v3_0;

import com.datastax.driver.core.Host;
import com.datastax.driver.core.exceptions.CoordinatorException;
import java.lang.reflect.Method;
import javax.annotation.Nullable;

// Driver 3.8.0 added SNI support and the EndPoint API it is built on. This instrumentation also
// supports 3.0 to 3.7, where neither exists, so they are reached by reflection. Host.getHostId() is
// reached the same way because it was added in 3.3.1.
class CassandraEndPoints {

  @Nullable
  private static final Class<?> SNI_END_POINT_CLASS =
      findClass("com.datastax.driver.core.SniEndPoint");

  @Nullable private static final Method HOST_GET_END_POINT = findMethod(Host.class, "getEndPoint");

  @Nullable private static final Method HOST_GET_HOST_ID = findMethod(Host.class, "getHostId");

  @Nullable
  private static final Method COORDINATOR_EXCEPTION_GET_END_POINT =
      findMethod(CoordinatorException.class, "getEndPoint");

  static boolean isSniEndPoint(Host coordinator) {
    return isSniEndPointInstance(invoke(HOST_GET_END_POINT, coordinator));
  }

  static boolean isSniEndPoint(CoordinatorException exception) {
    return isSniEndPointInstance(invoke(COORDINATOR_EXCEPTION_GET_END_POINT, exception));
  }

  @Nullable
  static String getHostId(Host coordinator) {
    Object hostId = invoke(HOST_GET_HOST_ID, coordinator);
    return hostId == null ? null : hostId.toString();
  }

  private static boolean isSniEndPointInstance(@Nullable Object endPoint) {
    return SNI_END_POINT_CLASS != null && SNI_END_POINT_CLASS.isInstance(endPoint);
  }

  @Nullable
  private static Object invoke(@Nullable Method method, Object target) {
    try {
      return method == null ? null : method.invoke(target);
    } catch (ReflectiveOperationException ignored) {
      return null;
    }
  }

  @Nullable
  private static Class<?> findClass(String name) {
    try {
      return Class.forName(name, false, Host.class.getClassLoader());
    } catch (ClassNotFoundException ignored) {
      return null;
    }
  }

  @Nullable
  private static Method findMethod(Class<?> type, String name) {
    try {
      return type.getMethod(name);
    } catch (NoSuchMethodException ignored) {
      return null;
    }
  }

  private CassandraEndPoints() {}
}
