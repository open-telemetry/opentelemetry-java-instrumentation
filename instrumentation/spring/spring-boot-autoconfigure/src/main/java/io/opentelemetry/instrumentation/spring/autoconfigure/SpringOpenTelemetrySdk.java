/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure;

import io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import io.opentelemetry.api.incubator.config.ConfigProvider;
import io.opentelemetry.sdk.OpenTelemetrySdk;

final class SpringOpenTelemetrySdk extends OpenTelemetrySdk implements ExtendedOpenTelemetry {

  private final ConfigProvider configProvider;

  SpringOpenTelemetrySdk(OpenTelemetrySdk delegate, ConfigProvider configProvider) {
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
