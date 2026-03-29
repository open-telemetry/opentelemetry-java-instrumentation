/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_56.incubator.config;

import io.opentelemetry.api.incubator.config.ConfigProvider;

public final class ApplicationConfigProvider156Incubator
    implements application.io.opentelemetry.api.incubator.config.ConfigProvider {

  private final application.io.opentelemetry.api.incubator.config.DeclarativeConfigProperties
      declarativeConfigProperties;

  public ApplicationConfigProvider156Incubator(ConfigProvider configProvider) {
    this.declarativeConfigProperties =
        new ApplicationDeclarativeConfigProperties156Incubator(
            configProvider.getInstrumentationConfig());
  }

  @Override
  public application.io.opentelemetry.api.incubator.config.DeclarativeConfigProperties
      getInstrumentationConfig() {
    return declarativeConfigProperties;
  }
}
