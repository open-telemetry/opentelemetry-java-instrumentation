package io.opentelemetry.instrumentation.jmx.internal;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at any time.
 */
public class JmxTelemetryRules {

  private JmxTelemetryRules(){
  }

  private static final Set<String> SUPPORTED_SYSTEMS = new HashSet<>(
      Arrays.asList("activemq", "camel", "experimental-cassandra", "experimental-kafka-connect", "hadoop", "jetty", "jvm", "tomcat", "wildfly"));

  public static Set<String> getSupportedSystems() {
    return SUPPORTED_SYSTEMS;
  }

  /**
   * Get list of rules resources for a given system to be loaded from class loader
   *
   * @param classLoader class loader
   * @param system system
   * @param includeInstable ignored for now
   * @return set of resources path(s) to load JMX rules from, empty if system is not supported
   */
  public static Set<String> locateRulesForSystem(ClassLoader classLoader, @Nullable String system,
      boolean includeInstable) {

    Set<String> result = new HashSet<>();
    String path = String.format("jmx/rules/%s.yaml", system);

    // TODO : add extra file(s) when including unstable metrics
    if (classLoader.getResource(path) != null) {
      result.add(path);
    }

    return result;
  }
}
