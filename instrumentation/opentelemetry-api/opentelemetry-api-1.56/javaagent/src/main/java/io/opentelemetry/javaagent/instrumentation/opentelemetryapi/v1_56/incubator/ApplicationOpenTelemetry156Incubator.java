/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_56.incubator;

import application.io.opentelemetry.api.OpenTelemetry;
import application.io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import application.io.opentelemetry.api.incubator.config.ConfigProvider;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_27.ApplicationOpenTelemetry127;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_56.incubator.config.ApplicationConfigProvider156Incubator;
import javax.annotation.Nullable;

public final class ApplicationOpenTelemetry156Incubator extends ApplicationOpenTelemetry127
    implements ExtendedOpenTelemetry {

  // Accessed with reflection
  @Nullable
  @SuppressWarnings("unused")
  public static final OpenTelemetry INSTANCE = create();

  private final ConfigProvider configProvider;

  @Nullable
  private static ApplicationOpenTelemetry156Incubator create() {
    io.opentelemetry.api.OpenTelemetry openTelemetry = GlobalOpenTelemetry.get();
    if (openTelemetry instanceof io.opentelemetry.api.incubator.ExtendedOpenTelemetry) {
      return new ApplicationOpenTelemetry156Incubator(
          ((io.opentelemetry.api.incubator.ExtendedOpenTelemetry) openTelemetry)
              .getConfigProvider());
    }
    return null;
  }

  public ApplicationOpenTelemetry156Incubator(
      io.opentelemetry.api.incubator.config.ConfigProvider configProvider) {
    this.configProvider = new ApplicationConfigProvider156Incubator(configProvider);
  }

  @Override
  public ConfigProvider getConfigProvider() {
    return configProvider;
  }
}
