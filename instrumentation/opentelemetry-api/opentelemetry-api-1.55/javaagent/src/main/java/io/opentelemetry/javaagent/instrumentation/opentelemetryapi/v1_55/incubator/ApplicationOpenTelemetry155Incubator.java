/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_55.incubator;

import application.io.opentelemetry.api.OpenTelemetry;
import application.io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import application.io.opentelemetry.api.incubator.config.ConfigProvider;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_27.ApplicationOpenTelemetry127;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_55.incubator.config.ApplicationConfigProvider155Incubator;

public class ApplicationOpenTelemetry155Incubator extends ApplicationOpenTelemetry127
    implements ExtendedOpenTelemetry {

  // Accessed with reflection
  @SuppressWarnings("unused")
  public static final OpenTelemetry INSTANCE = new ApplicationOpenTelemetry155Incubator();

  private final ConfigProvider configProvider =
      new ApplicationConfigProvider155Incubator(
          ((io.opentelemetry.api.incubator.ExtendedOpenTelemetry)
                  io.opentelemetry.api.GlobalOpenTelemetry.get())
              .getConfigProvider());

  @Override
  public ConfigProvider getConfigProvider() {
    return configProvider;
  }
}
