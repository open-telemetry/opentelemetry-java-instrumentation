/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hbase.client.common;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.TreeSet;
import javax.annotation.Nullable;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.zookeeper.ZKConfig;

public class HbaseServerTarget {

  private static final String REGISTRY_KEY = "hbase.client.registry.impl";
  private static final String ASYNC_REGISTRY_FACTORY =
      "org.apache.hadoop.hbase.client.AsyncRegistryFactory";
  private static final String ZK_ASYNC_REGISTRY = "org.apache.hadoop.hbase.client.ZKAsyncRegistry";
  private static final String ZK_REGISTRY = "org.apache.hadoop.hbase.client.ZKConnectionRegistry";
  private static final String MASTER_REGISTRY = "org.apache.hadoop.hbase.client.MasterRegistry";

  private static final String ZK_QUORUM_KEY = "hbase.zookeeper.quorum";
  private static final String ZK_CLIENT_PORT_KEY = "hbase.zookeeper.property.clientPort";
  private static final String CLIENT_ZK_QUORUM_KEY = "hbase.client.zookeeper.quorum";
  private static final String CLIENT_ZK_CLIENT_PORT_KEY =
      "hbase.client.zookeeper.property.clientPort";
  private static final String READ_ZK_CONFIG_KEY = "hbase.config.read.zookeeper.config";
  private static final String ZK_ZNODE_PARENT_KEY = "zookeeper.znode.parent";
  private static final String MASTER_ADDRESSES_KEY = "hbase.masters";
  private static final String MASTER_PORT_KEY = "hbase.master.port";

  private static final String DEFAULT_ZK_QUORUM = "localhost";
  private static final int DEFAULT_ZK_CLIENT_PORT = 2181;
  private static final String DEFAULT_ZK_ZNODE_PARENT = "/hbase";
  private static final int DEFAULT_MASTER_PORT = 16000;

  private static final boolean SUPPORTS_CLIENT_ZK_CONFIG =
      hasHbaseConstant("CLIENT_ZOOKEEPER_QUORUM");
  private static final boolean SUPPORTS_REGISTRY_CONFIG =
      hasHbaseConstant("CLIENT_CONNECTION_REGISTRY_IMPL_CONF_KEY")
          || hasClassField(ASYNC_REGISTRY_FACTORY, "REGISTRY_IMPL_CONF_KEY");
  private static final boolean SUPPORTS_ZK_CONFIG_FILE =
      hasHbaseConstant("HBASE_CONFIG_READ_ZOOKEEPER_CONFIG");
  private static final boolean USES_CONFIGURED_MASTER_PORT = usesConfiguredMasterPort();

  @Nullable
  public static String from(Configuration configuration) {
    return from(
        configuration,
        SUPPORTS_CLIENT_ZK_CONFIG,
        SUPPORTS_REGISTRY_CONFIG,
        SUPPORTS_ZK_CONFIG_FILE,
        USES_CONFIGURED_MASTER_PORT);
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
      return zkTarget(configuration, supportsClientZkConfig, supportsZkConfigFile);
    }

    registry = registry.trim();
    if (registry.equals(ZK_ASYNC_REGISTRY) || registry.equals(ZK_REGISTRY)) {
      return zkTarget(configuration, supportsClientZkConfig, supportsZkConfigFile);
    }
    if (registry.equals(MASTER_REGISTRY)) {
      return masterTarget(configuration, usesConfiguredMasterPort);
    }
    return null;
  }

  @Nullable
  private static String zkTarget(
      Configuration configuration, boolean supportsClientZkConfig, boolean supportsZkConfigFile) {
    // When supported and enabled, a usable zoo.cfg overrides the HBase ZooKeeper properties.
    if (supportsZkConfigFile
        && configuration.getBoolean(READ_ZK_CONFIG_KEY, false)
        && hasUsableZooCfg(configuration)) {
      return null;
    }

    String quorum = supportsClientZkConfig ? configuration.get(CLIENT_ZK_QUORUM_KEY) : null;
    String clientPort = null;
    if (quorum == null) {
      quorum = configuration.get(ZK_QUORUM_KEY, DEFAULT_ZK_QUORUM);
    } else {
      clientPort = configuration.get(CLIENT_ZK_CLIENT_PORT_KEY);
    }
    Set<String> hosts = canonicalEndpoints(quorum, null);
    if (hosts == null) {
      return null;
    }

    if (clientPort == null) {
      clientPort = configuration.get(ZK_CLIENT_PORT_KEY, Integer.toString(DEFAULT_ZK_CLIENT_PORT));
    }
    Integer parsedClientPort = parsePort(clientPort);
    if (parsedClientPort == null) {
      return null;
    }

    String znodeParent =
        sanitizeZnodeParent(configuration.get(ZK_ZNODE_PARENT_KEY, DEFAULT_ZK_ZNODE_PARENT));
    if (znodeParent == null) {
      return null;
    }
    return String.join(",", hosts) + ":" + parsedClientPort + ":" + znodeParent;
  }

  // HBase 1.x uses this deprecated parser when zoo.cfg support is enabled.
  @SuppressWarnings("deprecation")
  private static boolean hasUsableZooCfg(Configuration configuration) {
    try (InputStream inputStream = ZKConfig.class.getClassLoader().getResourceAsStream("zoo.cfg")) {
      if (inputStream == null) {
        return false;
      }
      ZKConfig.parseZooCfg(configuration, inputStream);
      return true;
    } catch (IOException ignored) {
      return false;
    }
  }

  @Nullable
  private static String masterTarget(
      Configuration configuration, boolean usesConfiguredMasterPort) {
    Integer defaultPort =
        masterDefaultPort(configuration.get(MASTER_PORT_KEY), usesConfiguredMasterPort);
    if (defaultPort == null) {
      return null;
    }
    Set<String> masters = canonicalEndpoints(configuration.get(MASTER_ADDRESSES_KEY), defaultPort);
    return masters == null ? null : String.join(",", masters);
  }

  @Nullable
  private static Integer masterDefaultPort(
      @Nullable String configuredPort, boolean usesConfiguredMasterPort) {
    if (!usesConfiguredMasterPort || configuredPort == null || configuredPort.trim().equals("0")) {
      return DEFAULT_MASTER_PORT;
    }
    return parsePort(configuredPort);
  }

  @Nullable
  private static Set<String> canonicalEndpoints(
      @Nullable String configuredEndpoints, @Nullable Integer defaultPort) {
    if (configuredEndpoints == null) {
      return null;
    }

    Set<String> endpoints = new TreeSet<>();
    for (String configuredEndpoint : configuredEndpoints.split(",", -1)) {
      String endpoint = canonicalEndpoint(configuredEndpoint, defaultPort);
      if (endpoint == null) {
        return null;
      }
      endpoints.add(endpoint);
    }
    return endpoints;
  }

  @Nullable
  private static String canonicalEndpoint(
      String configuredEndpoint, @Nullable Integer defaultPort) {
    String endpoint = sanitizeEndpoint(configuredEndpoint);
    if (endpoint == null) {
      return null;
    }

    String host;
    Integer port = null;
    if (endpoint.charAt(0) == '[') {
      int bracket = endpoint.indexOf(']');
      if (bracket <= 1) {
        return null;
      }
      host = endpoint.substring(0, bracket + 1);
      if (bracket + 1 < endpoint.length()) {
        if (endpoint.charAt(bracket + 1) != ':') {
          return null;
        }
        port = parsePort(endpoint.substring(bracket + 2));
        if (port == null) {
          return null;
        }
      }
    } else {
      int colon = endpoint.indexOf(':');
      if (colon >= 0) {
        if (colon == 0) {
          return null;
        }
        if (colon != endpoint.lastIndexOf(':')) {
          if (defaultPort == null) {
            return null;
          }
          host = "[" + endpoint + "]";
        } else {
          host = endpoint.substring(0, colon);
          port = parsePort(endpoint.substring(colon + 1));
          if (port == null) {
            return null;
          }
        }
      } else {
        host = endpoint;
      }
    }

    if (port == null) {
      port = defaultPort;
    }
    return port == null ? host : host + ":" + port;
  }

  @Nullable
  private static String sanitizeEndpoint(String configuredEndpoint) {
    String endpoint = configuredEndpoint.replaceAll("[\\t\\n\\x0B\\f\\r]", "").trim();
    for (int i = 0; i < endpoint.length(); i++) {
      char c = endpoint.charAt(i);
      if (c == '@' || c == '/' || c == '?' || c == '#' || Character.isWhitespace(c)) {
        return null;
      }
    }
    return endpoint.isEmpty() ? null : endpoint;
  }

  @Nullable
  private static String sanitizeZnodeParent(@Nullable String configuredParent) {
    if (configuredParent == null) {
      return null;
    }
    String parent = configuredParent.replaceAll("[\\t\\n\\x0B\\f\\r]", "").trim();
    return parent.length() > 1 && parent.charAt(0) == '/' && !parent.endsWith("/") ? parent : null;
  }

  @Nullable
  private static Integer parsePort(@Nullable String configuredPort) {
    if (configuredPort == null) {
      return null;
    }
    try {
      int port = Integer.parseInt(configuredPort.trim());
      return port > 0 && port <= 65535 ? port : null;
    } catch (NumberFormatException ignored) {
      return null;
    }
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

  private static boolean usesConfiguredMasterPort() {
    try {
      Method unused =
          Class.forName(MASTER_REGISTRY, false, HbaseServerTarget.class.getClassLoader())
              .getDeclaredMethod("getDefaultMasterPort", Configuration.class);
      return true;
    } catch (ReflectiveOperationException | SecurityException | LinkageError ignored) {
      return false;
    }
  }

  private HbaseServerTarget() {}
}
