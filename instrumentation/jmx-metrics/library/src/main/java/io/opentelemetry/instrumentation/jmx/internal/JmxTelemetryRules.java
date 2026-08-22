/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.internal;

import static java.util.Arrays.asList;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class JmxTelemetryRules {

  private static final Set<String> SUPPORTED_SYSTEMS =
      Collections.unmodifiableSet(
          new HashSet<>(
              asList(
                  "activemq",
                  "camel",
                  "experimental-cassandra",
                  "experimental-kafka-connect",
                  "hadoop",
                  "jetty",
                  "jvm",
                  "tomcat",
                  "wildfly")));

  public static Set<String> getSupportedSystems() {
    return SUPPORTED_SYSTEMS;
  }

  /**
   * Get list of rules resources for a given system to be loaded from class loader
   *
   * @param classLoader class loader
   * @param system system
   * @param includeUnstable {@literal true} to include unstable metrics definitions
   * @return matching JMX rule resource paths, empty if none are found
   */
  public static Set<String> locateRulesForSystem(
      ClassLoader classLoader, String system, boolean includeUnstable) {

    Set<String> result = new HashSet<>();

    String path = String.format("jmx/rules/%s.yaml", system);
    if (classLoader.getResource(path) != null) {
      result.add(path);
    }
    if (includeUnstable) {
      path = String.format("jmx/rules/%s_unstable.yaml", system);
      if (classLoader.getResource(path) != null) {
        result.add(path);
      }
    }
    return result;
  }

  private JmxTelemetryRules() {}
}
