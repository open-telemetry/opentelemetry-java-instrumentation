/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hbase.client.common;

import static io.opentelemetry.javaagent.instrumentation.hbase.client.common.HbaseServerEndpoint.canonicalEndpoint;
import static io.opentelemetry.javaagent.instrumentation.hbase.client.common.HbaseServerEndpoint.parsePort;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTarget;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTargetBuilder;
import java.lang.reflect.Method;
import javax.annotation.Nullable;
import org.apache.hadoop.conf.Configuration;

final class HbaseMasterTarget {

  private static final String MASTER_REGISTRY = "org.apache.hadoop.hbase.client.MasterRegistry";

  private static final String MASTER_ADDRESSES_KEY = "hbase.masters";
  private static final String MASTER_HOSTNAME_KEY = "hbase.master.hostname";
  private static final String MASTER_PORT_KEY = "hbase.master.port";

  private static final int DEFAULT_MASTER_PORT = 16000;

  private static final boolean USES_CONFIGURED_MASTER_PORT = detectConfiguredMasterPort();

  static boolean isRegistry(String registry) {
    return registry.equals(MASTER_REGISTRY);
  }

  @Nullable
  static String from(Configuration configuration) {
    return from(configuration, USES_CONFIGURED_MASTER_PORT);
  }

  @Nullable
  static String from(Configuration configuration, boolean usesConfiguredMasterPort) {
    Integer defaultPort = defaultPort(configuration.get(MASTER_PORT_KEY), usesConfiguredMasterPort);
    if (defaultPort == null) {
      return null;
    }
    String configuredMasters = addresses(configuration);
    if (configuredMasters == null) {
      return null;
    }

    DbServerTargetBuilder builder =
        DbServerTarget.builder(defaultPort).setSorted(true).setPortAlwaysInline(true);
    for (String configuredMaster : configuredMasters.split(",", -1)) {
      String endpoint = canonicalEndpoint(configuredMaster, defaultPort);
      if (endpoint == null) {
        return null;
      }
      int portSeparator = endpoint.lastIndexOf(':');
      Integer port = parsePort(endpoint.substring(portSeparator + 1));
      if (port == null) {
        return null;
      }
      builder.addEndpoint(endpoint.substring(0, portSeparator), port);
    }
    DbServerTarget target = builder.build();
    return target == null ? null : target.getAddress();
  }

  @Nullable
  private static String addresses(Configuration configuration) {
    String configuredMasters = configuration.get(MASTER_ADDRESSES_KEY);
    if (configuredMasters != null && !configuredMasters.isEmpty()) {
      return configuredMasters;
    }
    String configuredHostname = configuration.get(MASTER_HOSTNAME_KEY);
    if (configuredHostname != null && !configuredHostname.isEmpty()) {
      return configuredHostname + ":" + configuration.getInt(MASTER_PORT_KEY, DEFAULT_MASTER_PORT);
    }
    // MasterRegistry derives a hostname through DNS here; telemetry omits that implicit target.
    return null;
  }

  @Nullable
  private static Integer defaultPort(
      @Nullable String configuredPort, boolean usesConfiguredMasterPort) {
    if (!usesConfiguredMasterPort || configuredPort == null || configuredPort.trim().equals("0")) {
      return DEFAULT_MASTER_PORT;
    }
    return parsePort(configuredPort);
  }

  private static boolean detectConfiguredMasterPort() {
    try {
      Method unused =
          Class.forName(MASTER_REGISTRY, false, HbaseMasterTarget.class.getClassLoader())
              .getDeclaredMethod("getDefaultMasterPort", Configuration.class);
      return true;
    } catch (ReflectiveOperationException | SecurityException | LinkageError ignored) {
      return false;
    }
  }

  private HbaseMasterTarget() {}
}
