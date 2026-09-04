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

  // getSrvHost was added in 3.10; reflection keeps this compatible with older drivers
  @Nullable private static final Method GET_SRV_HOST = findGetSrvHost();

  private static final Configuration DIRECT_CONFIGURATION = new Configuration(true, null);
  private static final Configuration UNKNOWN_CONFIGURATION = new Configuration(false, null);

  private static final VirtualField<ClusterSettings.Builder, Configuration> BUILDER_CONFIGURATION =
      VirtualField.find(ClusterSettings.Builder.class, Configuration.class);
  private static final VirtualField<ClusterSettings, Configuration> SETTINGS_CONFIGURATION =
      VirtualField.find(ClusterSettings.class, Configuration.class);

  private static final ThreadLocal<MongoServerTarget> legacySrvTarget = new ThreadLocal<>();

  public static void initialize(ClusterSettings.Builder builder) {
    BUILDER_CONFIGURATION.set(builder, DIRECT_CONFIGURATION);
  }

  public static void hosts(ClusterSettings.Builder builder, List<ServerAddress> hosts) {
    BUILDER_CONFIGURATION.set(builder, new Configuration(true, MongoServerTarget.seeds(hosts)));
  }

  public static void connectionString(
      ClusterSettings.Builder builder, ConnectionString connectionString) {
    String value = connectionString.getConnectionString();
    if (!MongoServerTarget.isSrvConnectionString(value)) {
      List<ServerAddress> hosts = new ArrayList<>();
      for (String host : connectionString.getHosts()) {
        hosts.add(new ServerAddress(host));
      }
      BUILDER_CONFIGURATION.set(builder, new Configuration(true, MongoServerTarget.seeds(hosts)));
      return;
    }
    MongoServerTarget target = MongoServerTarget.srvConnectionString(value);
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
    MongoServerTarget scopedSrvTarget = legacySrvTarget.get();
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

  @Nullable
  public static LegacySrvTargetScope openLegacySrvTargetScope(@Nullable String connectionString) {
    MongoServerTarget target = MongoServerTarget.srvConnectionString(connectionString);
    if (target == null) {
      return null;
    }
    MongoServerTarget previous = legacySrvTarget.get();
    legacySrvTarget.set(target);
    return new LegacySrvTargetScope(previous);
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

    @Nullable private final MongoServerTarget previous;

    private LegacySrvTargetScope(@Nullable MongoServerTarget previous) {
      this.previous = previous;
    }

    public void close() {
      if (previous == null) {
        legacySrvTarget.remove();
      } else {
        legacySrvTarget.set(previous);
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
