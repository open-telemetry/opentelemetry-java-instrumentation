package io.opentelemetry.javaagent.tooling.config;

import io.opentelemetry.instrumentation.api.config.Config;

public final class AgentConfig {

  private static final AgentConfig instance = new AgentConfig(Config.get());

  private final Config config;

  public static AgentConfig get() {
    return instance;
  }

  public AgentConfig(Config config) {
    this.config = config;
  }

  public boolean isInstrumentationEnabled(
      Iterable<String> instrumentationNames, boolean defaultEnabled) {
    // If default is enabled, we want to enable individually,
    // if default is disabled, we want to disable individually.
    boolean anyEnabled = defaultEnabled;
    for (String name : instrumentationNames) {
      String propertyName = "otel.instrumentation." + name + ".enabled";
      boolean enabled = config.getBoolean(propertyName, defaultEnabled);

      if (defaultEnabled) {
        anyEnabled &= enabled;
      } else {
        anyEnabled |= enabled;
      }
    }
    return anyEnabled;
  }

  public boolean isDebugModeEnabled() {
    return config.getBoolean("otel.javaagent.debug", false);
  }
}
