package io.opentelemetry.javaagent.bootstrap.internal;

import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.api.incubator.config.EnabledInstrumentations;
import io.opentelemetry.instrumentation.api.incubator.config.internal.ExtendedDeclarativeConfigProperties;
import io.opentelemetry.instrumentation.api.internal.Initializer;

/**
 * Javaagent distribution-specific configuration.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public class AgentDistributionConfig {
  static EnabledInstrumentations enabledInstrumentations;
  static ExtendedDeclarativeConfigProperties distributionConfig;

  public static EnabledInstrumentations getEnabledInstrumentations() {
    return enabledInstrumentations;
  }

  @Initializer
  public static void setEnabledInstrumentations(EnabledInstrumentations enabledInstrumentations) {
    AgentDistributionConfig.enabledInstrumentations = enabledInstrumentations;
  }

  public static ExtendedDeclarativeConfigProperties get() {
    return distributionConfig;
  }

  @Initializer
  public static void set(DeclarativeConfigProperties distributionConfig) {
    AgentDistributionConfig.distributionConfig =
        new ExtendedDeclarativeConfigProperties(distributionConfig);
  }

  private AgentDistributionConfig() {}
}
