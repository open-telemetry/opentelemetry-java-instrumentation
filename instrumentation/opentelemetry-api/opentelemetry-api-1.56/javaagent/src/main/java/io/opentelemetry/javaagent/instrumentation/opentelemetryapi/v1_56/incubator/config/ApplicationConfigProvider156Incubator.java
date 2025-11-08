/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_56.incubator.config;

import application.io.opentelemetry.api.incubator.config.ConfigProvider;
import application.io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import javax.annotation.Nullable;

public class ApplicationConfigProvider156Incubator implements ConfigProvider {

  private final DeclarativeConfigProperties declarativeConfigProperties;

  public ApplicationConfigProvider156Incubator(
      io.opentelemetry.api.incubator.config.ConfigProvider configProvider) {
    this.declarativeConfigProperties =
        new ApplicationDeclarativeConfigProperties156Incubator(
            configProvider.getInstrumentationConfig());
  }

  @Nullable
  @Override
  public DeclarativeConfigProperties getInstrumentationConfig() {
    return declarativeConfigProperties;
  }
}
