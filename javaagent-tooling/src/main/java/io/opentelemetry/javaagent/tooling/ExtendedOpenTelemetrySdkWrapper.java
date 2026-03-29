/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import io.opentelemetry.api.incubator.config.ConfigProvider;
import io.opentelemetry.sdk.OpenTelemetrySdk;

final class ExtendedOpenTelemetrySdkWrapper extends OpenTelemetrySdk
    implements ExtendedOpenTelemetry {

  private final ConfigProvider configProvider;

  ExtendedOpenTelemetrySdkWrapper(OpenTelemetrySdk delegate, ConfigProvider configProvider) {
    super(
        delegate.getSdkTracerProvider(),
        delegate.getSdkMeterProvider(),
        delegate.getSdkLoggerProvider(),
        delegate.getPropagators());
    this.configProvider = configProvider;
  }

  @Override
  public ConfigProvider getConfigProvider() {
    return configProvider;
  }
}
