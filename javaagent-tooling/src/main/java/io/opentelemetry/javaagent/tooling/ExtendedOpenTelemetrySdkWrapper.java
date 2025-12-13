/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import io.opentelemetry.api.incubator.config.ConfigProvider;
import io.opentelemetry.sdk.OpenTelemetrySdk;

/**
 * Wrapper around {@link OpenTelemetrySdk} that implements {@link ExtendedOpenTelemetry} and
 * provides a {@link ConfigProvider} even when the underlying SDK doesn't have one.
 *
 * <p>This is used when the user configured with system properties (not YAML), so we create a
 * ConfigProvider backed by the final ConfigProperties. This allows instrumentations to always use
 * {@code ExtendedOpenTelemetry.getConfigProvider()} regardless of how the SDK was configured.
 */
public final class ExtendedOpenTelemetrySdkWrapper extends OpenTelemetrySdk
    implements ExtendedOpenTelemetry {

  private final ConfigProvider configProvider;

  public ExtendedOpenTelemetrySdkWrapper(OpenTelemetrySdk delegate, ConfigProvider configProvider) {
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
