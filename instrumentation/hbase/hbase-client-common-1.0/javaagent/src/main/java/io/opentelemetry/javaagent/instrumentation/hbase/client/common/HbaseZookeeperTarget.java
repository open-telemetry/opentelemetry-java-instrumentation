/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hbase.client.common;

import static io.opentelemetry.javaagent.instrumentation.hbase.client.common.HbaseServerEndpoint.canonicalEndpoint;
import static io.opentelemetry.javaagent.instrumentation.hbase.client.common.HbaseServerEndpoint.parsePort;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.zookeeper.ZKConfig;

final class HbaseZookeeperTarget {

  private static final String ZK_ASYNC_REGISTRY = "org.apache.hadoop.hbase.client.ZKAsyncRegistry";
  private static final String ZK_REGISTRY = "org.apache.hadoop.hbase.client.ZKConnectionRegistry";

  private static final String ZK_QUORUM_KEY = "hbase.zookeeper.quorum";
  private static final String ZK_CLIENT_PORT_KEY = "hbase.zookeeper.property.clientPort";
  private static final String CLIENT_ZK_QUORUM_KEY = "hbase.client.zookeeper.quorum";
  private static final String CLIENT_ZK_CLIENT_PORT_KEY =
      "hbase.client.zookeeper.property.clientPort";
  private static final String READ_ZK_CONFIG_KEY = "hbase.config.read.zookeeper.config";
  private static final String ZK_ZNODE_PARENT_KEY = "zookeeper.znode.parent";

  private static final String DEFAULT_ZK_QUORUM = "localhost";
  private static final int DEFAULT_ZK_CLIENT_PORT = 2181;
  private static final String DEFAULT_ZK_ZNODE_PARENT = "/hbase";

  private static final boolean SUPPORTS_CLIENT_ZK_CONFIG =
      hasHbaseConstant("CLIENT_ZOOKEEPER_QUORUM");
  @Nullable private static final Method PARSE_ZOO_CFG = findParseZooCfg();
  private static final boolean SUPPORTS_ZK_CONFIG_FILE =
      hasHbaseConstant("HBASE_CONFIG_READ_ZOOKEEPER_CONFIG") && PARSE_ZOO_CFG != null;

  static boolean isRegistry(String registry) {
    return registry.equals(ZK_ASYNC_REGISTRY) || registry.equals(ZK_REGISTRY);
  }

  @Nullable
  static String from(Configuration configuration) {
    return from(configuration, SUPPORTS_CLIENT_ZK_CONFIG, SUPPORTS_ZK_CONFIG_FILE);
  }

  @Nullable
  static String from(
      Configuration configuration, boolean supportsClientZkConfig, boolean supportsZkConfigFile) {
    // When supported and enabled, a usable zoo.cfg overrides the HBase ZooKeeper properties.
    if (supportsZkConfigFile
        && configuration.getBoolean(READ_ZK_CONFIG_KEY, false)
        && hasUsableZooCfg(configuration)) {
      return resolvedTarget(configuration);
    }

    String quorum = supportsClientZkConfig ? configuration.get(CLIENT_ZK_QUORUM_KEY) : null;
    String clientPort = null;
    if (quorum == null) {
      quorum = configuration.get(ZK_QUORUM_KEY, DEFAULT_ZK_QUORUM);
    } else {
      clientPort = configuration.get(CLIENT_ZK_CLIENT_PORT_KEY);
    }
    quorum = sanitizeQuorum(quorum);
    if (quorum == null) {
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
    return quorum + ":" + parsedClientPort + ":" + znodeParent;
  }

  @Nullable
  private static String resolvedTarget(Configuration configuration) {
    String quorumServers;
    try {
      quorumServers = ZKConfig.getZKQuorumServersString(configuration);
    } catch (IndexOutOfBoundsException | SecurityException | LinkageError ignored) {
      return null;
    }

    if (quorumServers == null) {
      return null;
    }

    List<String> hosts = new ArrayList<>();
    Integer clientPort = null;
    for (String configuredEndpoint : quorumServers.split(",", -1)) {
      String endpoint = canonicalEndpoint(configuredEndpoint, null);
      if (endpoint == null) {
        return null;
      }
      int portSeparator = endpoint.lastIndexOf(':');
      if (portSeparator <= 0) {
        return null;
      }
      Integer endpointPort = parsePort(endpoint.substring(portSeparator + 1));
      if (endpointPort == null || (clientPort != null && !clientPort.equals(endpointPort))) {
        return null;
      }
      clientPort = endpointPort;
      hosts.add(endpoint.substring(0, portSeparator));
    }

    String znodeParent =
        sanitizeZnodeParent(configuration.get(ZK_ZNODE_PARENT_KEY, DEFAULT_ZK_ZNODE_PARENT));
    if (hosts.isEmpty() || clientPort == null || znodeParent == null) {
      return null;
    }
    hosts.sort(String::compareTo);
    return String.join(",", hosts) + ":" + clientPort + ":" + znodeParent;
  }

  private static boolean hasUsableZooCfg(Configuration configuration) {
    if (PARSE_ZOO_CFG == null) {
      return false;
    }
    try (InputStream inputStream = ZKConfig.class.getClassLoader().getResourceAsStream("zoo.cfg")) {
      if (inputStream == null) {
        return false;
      }
      PARSE_ZOO_CFG.invoke(null, configuration, inputStream);
      return true;
    } catch (IOException
        | ReflectiveOperationException
        | SecurityException
        | LinkageError ignored) {
      return false;
    }
  }

  // Only HBase 1.x parses zoo.cfg, so this deprecated parser is looked up reflectively to keep the
  // HBase 2.x instrumentation from being rejected over a method that version does not have.
  @Nullable
  private static Method findParseZooCfg() {
    try {
      return ZKConfig.class.getMethod("parseZooCfg", Configuration.class, InputStream.class);
    } catch (NoSuchMethodException | SecurityException | LinkageError ignored) {
      return null;
    }
  }

  @Nullable
  private static String sanitizeQuorum(@Nullable String configuredQuorum) {
    if (configuredQuorum == null) {
      return null;
    }
    String quorum = configuredQuorum.replaceAll("[\\t\\n\\x0B\\f\\r]", "");
    List<String> endpoints = new ArrayList<>();
    for (String endpoint : quorum.split(",", -1)) {
      String canonicalEndpoint = canonicalQuorumEndpoint(endpoint);
      if (canonicalEndpoint == null) {
        return null;
      }
      endpoints.add(canonicalEndpoint);
    }
    return String.join(",", endpoints);
  }

  @Nullable
  private static String canonicalQuorumEndpoint(String configuredEndpoint) {
    String endpoint = HbaseServerEndpoint.sanitizeEndpoint(configuredEndpoint);
    if (endpoint == null || !endpoint.equals(configuredEndpoint)) {
      return null;
    }
    if (endpoint.charAt(0) != '['
        && endpoint.indexOf(':') != endpoint.lastIndexOf(':')
        && HbaseServerEndpoint.isIpv6Address(endpoint)) {
      return "[" + endpoint + "]";
    }
    return endpoint.equals(canonicalEndpoint(endpoint, null)) ? endpoint : null;
  }

  @Nullable
  private static String sanitizeZnodeParent(@Nullable String configuredParent) {
    if (configuredParent == null) {
      return null;
    }
    String parent = configuredParent.replaceAll("[\\t\\n\\x0B\\f\\r]", "").trim();
    return parent.length() > 1 && parent.charAt(0) == '/' && !parent.endsWith("/") ? parent : null;
  }

  private static boolean hasHbaseConstant(String fieldName) {
    try {
      Field unused = HConstants.class.getField(fieldName);
      return true;
    } catch (NoSuchFieldException | SecurityException | LinkageError ignored) {
      return false;
    }
  }

  private HbaseZookeeperTarget() {}
}
