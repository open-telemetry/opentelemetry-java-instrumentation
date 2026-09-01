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
public class InternalMetricsDefinitions {

  private static final Set<String> SUPPORTED_SYSTEMS =
      Collections.unmodifiableSet(
          new HashSet<>(
              asList(
                  "activemq",
                  "camel",
                  "cassandra",
                  "kafka-broker",
                  "kafka-connect",
                  "hadoop",
                  "jetty",
                  "jvm",
                  "tomcat",
                  "wildfly")));

  private final ClassLoader classLoader;

  public InternalMetricsDefinitions(ClassLoader classLoader) {
    this.classLoader = classLoader;
  }

  public static Set<String> getSupportedSystems() {
    return SUPPORTED_SYSTEMS;
  }

  /**
   * Get resource paths for rules for a given system
   *
   * @param system system identifier
   * @param includeStable whether to include stable rules
   * @param includeUnstable whether to include unstable rules
   * @return collection of resource paths to load rules, empty if system is not supported
   */
  public Set<String> getRulesForSystem(
      String system, boolean includeStable, boolean includeUnstable) {
    Set<String> result = new HashSet<>();

    // preserve compatibility with existing rules until we rename them
    switch (system) {
      case "cassandra":
      case "kafka-broker":
      case "kafka-connect":
        system = "experimental-" + system;
        break;
      default:
        // intentionally empty
    }

    String stablePath = String.format("jmx/rules/%s.yaml", system);
    if (includeStable && classLoader.getResource(stablePath) != null) {
      result.add(stablePath);
    }

    String unstablePath = String.format("jmx/rules/%s_unstable.yaml", system);
    if (includeUnstable) {
      if (classLoader.getResource(unstablePath) != null) {
        result.add(unstablePath);
      }
    }

    return result;
  }
}
