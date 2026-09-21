/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.mongo.v3_1.internal;

import com.mongodb.ConnectionString;
import com.mongodb.ServerAddress;
import com.mongodb.connection.ClusterSettings;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class MongoClusterSettings {

  private static final String SRV_SCHEME = "mongodb+srv://";

  // getSrvHost was added in 3.10; reflection keeps this compatible with older drivers
  @Nullable private static final Method GET_SRV_HOST = findGetSrvHost();

  private static final Configuration DIRECT_CONFIGURATION = new Configuration(true, null);
  private static final Configuration UNKNOWN_CONFIGURATION = new Configuration(false, null);

  private static final VirtualField<ClusterSettings.Builder, Configuration> BUILDER_CONFIGURATION =
      VirtualField.find(ClusterSettings.Builder.class, Configuration.class);
  private static final VirtualField<ClusterSettings, Configuration> SETTINGS_CONFIGURATION =
      VirtualField.find(ClusterSettings.class, Configuration.class);

  // Mongo#createCluster advice produces this one-shot handoff for ClusterSettings.Builder#build
  // advice, which consumes it in built(). If the build does not complete,
  // LegacySrvTargetScope.close() removes it when createCluster exits.
  private static final ThreadLocal<LegacySrvTargetScope> legacySrvTargetScope = new ThreadLocal<>();

  public static void initialize(ClusterSettings.Builder builder) {
    BUILDER_CONFIGURATION.set(builder, DIRECT_CONFIGURATION);
  }

  public static void hosts(ClusterSettings.Builder builder, List<ServerAddress> hosts) {
    BUILDER_CONFIGURATION.set(builder, new Configuration(true, MongoServerTarget.seeds(hosts)));
  }

  public static void connectionString(
      ClusterSettings.Builder builder, ConnectionString connectionString) {
    String value = connectionString.getConnectionString();
    if (!isSrvConnectionString(value)) {
      List<ServerAddress> hosts = new ArrayList<>();
      for (String host : connectionString.getHosts()) {
        hosts.add(new ServerAddress(host));
      }
      BUILDER_CONFIGURATION.set(builder, new Configuration(true, MongoServerTarget.seeds(hosts)));
      return;
    }
    MongoServerTarget target = srvConnectionString(value);
    BUILDER_CONFIGURATION.set(
        builder, target == null ? UNKNOWN_CONFIGURATION : new Configuration(false, target));
  }

  public static void captureSrvHost(ClusterSettings.Builder builder, String srvHost) {
    MongoServerTarget target = MongoServerTarget.srvHost(srvHost);
    BUILDER_CONFIGURATION.set(
        builder, target == null ? UNKNOWN_CONFIGURATION : new Configuration(false, target));
  }

  public static void applySettings(
      ClusterSettings.Builder builder, ClusterSettings sourceSettings) {
    Configuration configuration = SETTINGS_CONFIGURATION.get(sourceSettings);
    BUILDER_CONFIGURATION.set(
        builder, configuration == null ? UNKNOWN_CONFIGURATION : configuration);
  }

  public static void built(ClusterSettings.Builder builder, ClusterSettings settings) {
    if (SETTINGS_CONFIGURATION.get(settings) != null) {
      return;
    }
    LegacySrvTargetScope scope = legacySrvTargetScope.get();
    if (scope != null) {
      legacySrvTargetScope.remove();
    }
    MongoServerTarget scopedSrvTarget = scope == null ? null : scope.target;
    Configuration configuration =
        scopedSrvTarget == null
            ? BUILDER_CONFIGURATION.get(builder)
            : new Configuration(false, scopedSrvTarget);
    SETTINGS_CONFIGURATION.set(
        settings, configuration == null ? UNKNOWN_CONFIGURATION : configuration);
  }

  @Nullable
  public static MongoServerTarget configuredTarget(ClusterSettings settings) {
    String nativeSrvHost = srvHost(settings);
    if (nativeSrvHost != null) {
      return MongoServerTarget.srvHost(nativeSrvHost);
    }
    Configuration configuration = SETTINGS_CONFIGURATION.get(settings);
    if (configuration != null && configuration.target != null) {
      return configuration.target;
    }
    if (configuration != null && !configuration.direct) {
      return null;
    }
    if (GET_SRV_HOST != null) {
      return MongoServerTarget.seeds(settings.getHosts());
    }

    if (configuration == null) {
      return null;
    }
    return MongoServerTarget.seeds(settings.getHosts());
  }

  public static LegacySrvTargetScope openLegacySrvTargetScope(@Nullable String connectionString) {
    MongoServerTarget target = srvConnectionString(connectionString);
    LegacySrvTargetScope scope = new LegacySrvTargetScope(legacySrvTargetScope.get(), target);
    legacySrvTargetScope.set(scope);
    return scope;
  }

  @Nullable
  static MongoServerTarget srvConnectionString(@Nullable String connectionString) {
    if (connectionString == null || !isSrvConnectionString(connectionString)) {
      return null;
    }
    return MongoServerTarget.srvHost(sanitizeSrvHost(connectionString));
  }

  private static boolean isSrvConnectionString(String connectionString) {
    return connectionString.regionMatches(true, 0, SRV_SCHEME, 0, SRV_SCHEME.length());
  }

  private static String sanitizeSrvHost(String value) {
    int schemeSeparator = value.indexOf("://");
    String host = schemeSeparator < 0 ? value : value.substring(schemeSeparator + 3);
    int end = host.length();
    for (char separator : new char[] {'/', '?', '#'}) {
      int index = host.indexOf(separator);
      if (index >= 0 && index < end) {
        end = index;
      }
    }
    host = host.substring(0, end);
    int credentialsSeparator = host.lastIndexOf('@');
    return credentialsSeparator < 0 ? host : host.substring(credentialsSeparator + 1);
  }

  @Nullable
  private static String srvHost(ClusterSettings settings) {
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

  /**
   * This class is internal and is hence not for public use. Its APIs are unstable and can change at
   * any time.
   */
  public static class LegacySrvTargetScope {

    @Nullable private final LegacySrvTargetScope previous;
    @Nullable private final MongoServerTarget target;

    private LegacySrvTargetScope(
        @Nullable LegacySrvTargetScope previous, @Nullable MongoServerTarget target) {
      this.previous = previous;
      this.target = target;
    }

    public void close() {
      if (previous == null) {
        legacySrvTargetScope.remove();
      } else {
        legacySrvTargetScope.set(previous);
      }
    }
  }

  private static class Configuration {

    private final boolean direct;
    @Nullable private final MongoServerTarget target;

    private Configuration(boolean direct, @Nullable MongoServerTarget target) {
      this.direct = direct;
      this.target = target;
    }
  }

  private MongoClusterSettings() {}
}
