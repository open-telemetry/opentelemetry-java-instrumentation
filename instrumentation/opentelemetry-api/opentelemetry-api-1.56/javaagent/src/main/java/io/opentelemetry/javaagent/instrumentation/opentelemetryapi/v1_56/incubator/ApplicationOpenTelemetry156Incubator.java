/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_56.incubator;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import io.opentelemetry.api.incubator.config.ConfigProvider;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_27.ApplicationOpenTelemetry127;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_56.incubator.config.ApplicationConfigProvider156Incubator;
import javax.annotation.Nullable;

public final class ApplicationOpenTelemetry156Incubator extends ApplicationOpenTelemetry127
    implements application.io.opentelemetry.api.incubator.ExtendedOpenTelemetry {

  // Accessed with reflection
  @Nullable
  @SuppressWarnings("unused")
  public static final application.io.opentelemetry.api.OpenTelemetry INSTANCE = create();

  private final application.io.opentelemetry.api.incubator.config.ConfigProvider configProvider;

  @Nullable
  private static ApplicationOpenTelemetry156Incubator create() {
    OpenTelemetry openTelemetry = GlobalOpenTelemetry.get();
    if (openTelemetry instanceof ExtendedOpenTelemetry) {
      return new ApplicationOpenTelemetry156Incubator(
          ((ExtendedOpenTelemetry) openTelemetry).getConfigProvider());
    }
    return null;
  }

  public ApplicationOpenTelemetry156Incubator(ConfigProvider configProvider) {
    this.configProvider = new ApplicationConfigProvider156Incubator(configProvider);
  }

  @Override
  public application.io.opentelemetry.api.incubator.config.ConfigProvider getConfigProvider() {
    return configProvider;
  }
}
