/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hbase.client.common;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import java.lang.reflect.Field;
import javax.annotation.Nullable;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.ipc.AbstractRpcClient;

/**
 * Builds logical HBase server targets from the client configuration.
 *
 * <p>ZooKeeper targets use HBase's cluster-key shape {@code <quorum>:<clientPort>:<znodeParent>}.
 * The shared module compiles against HBase 1.0.0, which does not have {@code
 * ZKConfig.getZooKeeperClusterKey}, so target construction cannot be delegated to that method.
 * Implementations in newer versions also do not handle every supported source and path:
 * client-specific ZooKeeper configuration, legacy {@code zoo.cfg}, or {@code MasterRegistry}. This
 * class also validates and canonicalizes targets before emitting them as telemetry.
 */
public class HbaseServerTarget {

  private static final String REGISTRY_KEY = "hbase.client.registry.impl";
  private static final String ASYNC_REGISTRY_FACTORY =
      "org.apache.hadoop.hbase.client.AsyncRegistryFactory";

  private static final boolean SUPPORTS_REGISTRY_CONFIG =
      hasHbaseConstant("CLIENT_CONNECTION_REGISTRY_IMPL_CONF_KEY")
          || hasClassField(ASYNC_REGISTRY_FACTORY, "REGISTRY_IMPL_CONF_KEY");

  public static void store(AbstractRpcClient client, Configuration configuration) {
    if (!emitStableDatabaseSemconv()) {
      return;
    }
    String serverTarget = from(configuration);
    if (serverTarget != null) {
      ServerTargetHolder.SERVER_TARGET.set(client, serverTarget);
    }
  }

  @Nullable
  public static String get(AbstractRpcClient client) {
    return emitStableDatabaseSemconv() ? ServerTargetHolder.SERVER_TARGET.get(client) : null;
  }

  @Nullable
  static String from(Configuration configuration) {
    String registry = SUPPORTS_REGISTRY_CONFIG ? configuration.get(REGISTRY_KEY) : null;
    if (registry == null) {
      return HbaseZookeeperTarget.from(configuration);
    }

    registry = registry.trim();
    if (HbaseZookeeperTarget.isRegistry(registry)) {
      return HbaseZookeeperTarget.from(configuration);
    }
    if (HbaseMasterTarget.isRegistry(registry)) {
      return HbaseMasterTarget.from(configuration);
    }
    return null;
  }

  @Nullable
  static String from(
      Configuration configuration,
      boolean supportsClientZkConfig,
      boolean supportsRegistryConfig,
      boolean usesConfiguredMasterPort) {
    return from(
        configuration,
        supportsClientZkConfig,
        supportsRegistryConfig,
        true,
        usesConfiguredMasterPort);
  }

  @Nullable
  static String from(
      Configuration configuration,
      boolean supportsClientZkConfig,
      boolean supportsRegistryConfig,
      boolean supportsZkConfigFile,
      boolean usesConfiguredMasterPort) {
    String registry = supportsRegistryConfig ? configuration.get(REGISTRY_KEY) : null;
    if (registry == null) {
      return HbaseZookeeperTarget.from(configuration, supportsClientZkConfig, supportsZkConfigFile);
    }

    registry = registry.trim();
    if (HbaseZookeeperTarget.isRegistry(registry)) {
      return HbaseZookeeperTarget.from(configuration, supportsClientZkConfig, supportsZkConfigFile);
    }
    if (HbaseMasterTarget.isRegistry(registry)) {
      return HbaseMasterTarget.from(configuration, usesConfiguredMasterPort);
    }
    return null;
  }

  private static boolean hasHbaseConstant(String fieldName) {
    try {
      Field unused = HConstants.class.getField(fieldName);
      return true;
    } catch (NoSuchFieldException | SecurityException | LinkageError ignored) {
      return false;
    }
  }

  private static boolean hasClassField(String className, String fieldName) {
    try {
      Field unused =
          Class.forName(className, false, HbaseServerTarget.class.getClassLoader())
              .getDeclaredField(fieldName);
      return true;
    } catch (ReflectiveOperationException | SecurityException | LinkageError ignored) {
      return false;
    }
  }

  private static class ServerTargetHolder {
    private static final VirtualField<AbstractRpcClient, String> SERVER_TARGET =
        VirtualField.find(AbstractRpcClient.class, String.class);
  }

  private HbaseServerTarget() {}
}
