package io.opentelemetry.instrumentation.spring.autoconfigure.internal;

import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;

public class InstrumentationConfigUtil {
  private InstrumentationConfigUtil() {
  }

  public static boolean isStatementSanitizationEnabled(ConfigProperties config, String key) {
    return config.getBoolean(
        key, config.getBoolean("otel.instrumentation.common.db-statement-sanitizer.enabled", true));
  }
}
