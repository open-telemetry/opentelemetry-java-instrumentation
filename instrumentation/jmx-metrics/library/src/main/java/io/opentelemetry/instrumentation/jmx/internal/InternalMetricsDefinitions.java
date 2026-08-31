package io.opentelemetry.instrumentation.jmx.internal;

import java.util.Set;

public class InternalMetricsDefinitions {

  public InternalMetricsDefinitions() {}

  public Set<String> getSupportedSystems() {
    // list of all supported systems
    // TODO: need to build and maintain an explicit list of all the systems and their resource path.
    return null;
  }

  public String getRulesPathForSystem(String system) {
    // path to a given resource file for system, allows to provide migration options when we want to rename/promote things
    return null;
  }

  public boolean isStableMetric(String system, String metricName) {
    // current heuristic on target system name
    if(system.startsWith("experimental-")){
      return false;
    }
    // lookup in the metric registry
    return false;
  }
}
