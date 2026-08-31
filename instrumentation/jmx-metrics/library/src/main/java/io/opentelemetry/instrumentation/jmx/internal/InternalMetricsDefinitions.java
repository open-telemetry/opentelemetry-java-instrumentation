package io.opentelemetry.instrumentation.jmx.internal;

import java.util.Collections;
import java.util.Set;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at any time.
 */
public class InternalMetricsDefinitions {

  public InternalMetricsDefinitions() {}

  public Set<String> getSupportedSystems() {
    return Collections.emptySet();
  }

  public Set<String> getRulesForSystem(String system, boolean includeStable, boolean includeUnstable) {
    return Collections.emptySet();
  }

}
