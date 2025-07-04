package io.opentelemetry.javaagent.tooling.config;

import io.opentelemetry.sdk.autoconfigure.spi.internal.DefaultConfigProperties;
import javax.annotation.Nullable;
import java.util.Collections;

public interface EarlyInitAgentConfig {
  @Nullable
  String getString(String propertyName);

  boolean getBoolean(String propertyName, boolean defaultValue);

  int getInt(String propertyName, int defaultValue);

  void logEarlyConfigErrorsIfAny();

  static EarlyInitAgentConfig create() {
    String configurationFile =
        DefaultConfigProperties.create(Collections.emptyMap())
            .getString("otel.experimental.config.file");

    return configurationFile != null
        ? new DeclarativeConfigEarlyInitAgentConfig(configurationFile)
        : new LegacyConfigFileEarlyInitAgentConfig();
  }
}
