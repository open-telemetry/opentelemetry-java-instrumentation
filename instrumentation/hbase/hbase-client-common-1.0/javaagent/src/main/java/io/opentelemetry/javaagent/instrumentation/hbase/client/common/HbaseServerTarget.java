/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hbase.client.common;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.ipc.AbstractRpcClient;
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
  private static final String MASTER_HOSTNAME_KEY = "hbase.master.hostname";
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
  @Nullable private static final Method PARSE_ZOO_CFG = findParseZooCfg();
  private static final boolean SUPPORTS_ZK_CONFIG_FILE =
      hasHbaseConstant("HBASE_CONFIG_READ_ZOOKEEPER_CONFIG") && PARSE_ZOO_CFG != null;
  private static final boolean USES_CONFIGURED_MASTER_PORT = usesConfiguredMasterPort();

  public static void store(AbstractRpcClient client, Configuration configuration) {
    if (!emitStableDatabaseSemconv()) {
      return;
    }
    String serverTarget = from(configuration);
    if (serverTarget != null) {
      ServerTargetVirtualField.SERVER_TARGET.set(client, serverTarget);
    }
  }

  @Nullable
  public static String get(AbstractRpcClient client) {
    return emitStableDatabaseSemconv() ? ServerTargetVirtualField.SERVER_TARGET.get(client) : null;
  }

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
      return resolvedZkTarget(configuration);
    }

    String quorum = supportsClientZkConfig ? configuration.get(CLIENT_ZK_QUORUM_KEY) : null;
    String clientPort = null;
    if (quorum == null) {
      quorum = configuration.get(ZK_QUORUM_KEY, DEFAULT_ZK_QUORUM);
    } else {
      clientPort = configuration.get(CLIENT_ZK_CLIENT_PORT_KEY);
    }
    quorum = sanitizeZkQuorum(quorum);
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
  private static String resolvedZkTarget(Configuration configuration) {
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
  private static String masterTarget(
      Configuration configuration, boolean usesConfiguredMasterPort) {
    Integer defaultPort =
        masterDefaultPort(configuration.get(MASTER_PORT_KEY), usesConfiguredMasterPort);
    if (defaultPort == null) {
      return null;
    }
    List<String> masters = canonicalEndpoints(masterAddresses(configuration), defaultPort);
    return masters == null ? null : String.join(",", masters);
  }

  @Nullable
  private static String masterAddresses(Configuration configuration) {
    String configuredMasters = configuration.get(MASTER_ADDRESSES_KEY);
    if (configuredMasters != null && !configuredMasters.isEmpty()) {
      return configuredMasters;
    }
    String configuredHostname = configuration.get(MASTER_HOSTNAME_KEY);
    if (configuredHostname != null && !configuredHostname.isEmpty()) {
      return configuredHostname + ":" + configuration.getInt(MASTER_PORT_KEY, DEFAULT_MASTER_PORT);
    }
    try {
      Method getMasterAddr =
          Class.forName(MASTER_REGISTRY, false, HbaseServerTarget.class.getClassLoader())
              .getDeclaredMethod("getMasterAddr", Configuration.class);
      getMasterAddr.setAccessible(true);
      return (String) getMasterAddr.invoke(null, configuration);
    } catch (ReflectiveOperationException | SecurityException | LinkageError ignored) {
      return null;
    }
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
  private static List<String> canonicalEndpoints(
      @Nullable String configuredEndpoints, @Nullable Integer defaultPort) {
    if (configuredEndpoints == null) {
      return null;
    }

    List<String> endpoints = new ArrayList<>();
    for (String configuredEndpoint : configuredEndpoints.split(",", -1)) {
      String endpoint = canonicalEndpoint(configuredEndpoint, defaultPort);
      if (endpoint == null) {
        return null;
      }
      endpoints.add(endpoint);
    }
    endpoints.sort(String::compareTo);
    return endpoints;
  }

  @Nullable
  private static String sanitizeZkQuorum(@Nullable String configuredQuorum) {
    if (configuredQuorum == null) {
      return null;
    }
    String quorum = configuredQuorum.replaceAll("[\\t\\n\\x0B\\f\\r]", "");
    List<String> endpoints = new ArrayList<>();
    for (String endpoint : quorum.split(",", -1)) {
      String canonicalEndpoint = canonicalZkQuorumEndpoint(endpoint);
      if (canonicalEndpoint == null) {
        return null;
      }
      endpoints.add(canonicalEndpoint);
    }
    return String.join(",", endpoints);
  }

  @Nullable
  private static String canonicalZkQuorumEndpoint(String configuredEndpoint) {
    String endpoint = sanitizeEndpoint(configuredEndpoint);
    if (endpoint == null || !endpoint.equals(configuredEndpoint)) {
      return null;
    }
    if (endpoint.charAt(0) != '['
        && endpoint.indexOf(':') != endpoint.lastIndexOf(':')
        && isIpv6Address(endpoint)) {
      return "[" + endpoint + "]";
    }
    return endpoint.equals(canonicalEndpoint(endpoint, null)) ? endpoint : null;
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
      if (!isIpv6Address(endpoint.substring(1, bracket))) {
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
          if (defaultPort == null || !isIpv6Address(endpoint)) {
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

  private static boolean isIpv6Address(String address) {
    try {
      return InetAddress.getByName(address) instanceof Inet6Address;
    } catch (UnknownHostException ignored) {
      return false;
    }
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

  private static class ServerTargetVirtualField {
    private static final VirtualField<AbstractRpcClient, String> SERVER_TARGET =
        VirtualField.find(AbstractRpcClient.class, String.class);
  }

  private HbaseServerTarget() {}
}
