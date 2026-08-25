/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.mongo.v3_1.internal;

import com.mongodb.connection.ClusterSettings;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import javax.annotation.Nullable;

/**
 * Reads the SRV host out of cluster settings, which only a driver from 3.10 on keeps.
 *
 * <p>Muzzle holds the instrumentation for the older drivers to the API they shipped, so the
 * accessor is looked up reflectively rather than called directly. That lookup runs once, when this
 * class is initialized. The accessor is then invoked while a cluster is being constructed, once per
 * client, and never while a command runs.
 *
 * <p>A driver before 3.10 resolves an SRV host into seeds as it parses the connection string, so
 * there is nothing left for this to read and the client is described by the hosts it resolved to.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class MongoClusterSettings {

  @Nullable private static final Method GET_SRV_HOST = findGetSrvHost();

  /** The SRV host {@code settings} names, or {@code null} when it names none. */
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
