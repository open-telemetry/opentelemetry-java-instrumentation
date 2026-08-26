/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.mongo.v3_1.internal;

import com.mongodb.connection.ClusterSettings;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import javax.annotation.Nullable;

public final class MongoClusterSettings {

  // getSrvHost was added in 3.10; reflection keeps this compatible with older drivers
  @Nullable private static final Method GET_SRV_HOST = findGetSrvHost();

  @Nullable
  public static String srvHost(ClusterSettings settings) {
    if (GET_SRV_HOST == null) {
      return null;
    }
    try {
      return (String) GET_SRV_HOST.invoke(settings);
    } catch (IllegalAccessException | InvocationTargetException ignored) {
      return null;
    }
  }

  @Nullable
  private static Method findGetSrvHost() {
    try {
      return ClusterSettings.class.getMethod("getSrvHost");
    } catch (NoSuchMethodException ignored) {
      return null;
    }
  }

  private MongoClusterSettings() {}
}
