/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import io.opentelemetry.api.incubator.config.ConfigProvider;
import io.opentelemetry.api.logs.LoggerProvider;
import io.opentelemetry.api.metrics.MeterProvider;
import io.opentelemetry.api.trace.TracerProvider;
import io.opentelemetry.context.propagation.ContextPropagators;

/**
 * Wrapper around {@link OpenTelemetry} that implements {@link ExtendedOpenTelemetry} and provides a
 * {@link ConfigProvider} even when the underlying SDK doesn't have one.
 *
 * <p>This is used when the user configured with system properties (not YAML), so we create a
 * ConfigProvider backed by the final ConfigProperties. This allows instrumentations to always use
 * {@code ExtendedOpenTelemetry.getConfigProvider()} regardless of how the SDK was configured.
 */
public final class ExtendedOpenTelemetrySdkWrapper implements ExtendedOpenTelemetry {

  private final OpenTelemetry delegate;
  private final ConfigProvider configProvider;

  /**
   * Creates a new wrapper.
   *
   * @param delegate the OpenTelemetry instance to wrap
   * @param configProvider the ConfigProvider to return from getConfigProvider()
   */
  public ExtendedOpenTelemetrySdkWrapper(OpenTelemetry delegate, ConfigProvider configProvider) {
    this.delegate = delegate;
    this.configProvider = configProvider;
  }

  @Override
  public ConfigProvider getConfigProvider() {
    return configProvider;
  }

  @Override
  public TracerProvider getTracerProvider() {
    return delegate.getTracerProvider();
  }

  @Override
  public MeterProvider getMeterProvider() {
    return delegate.getMeterProvider();
  }

  @Override
  public LoggerProvider getLogsBridge() {
    return delegate.getLogsBridge();
  }

  @Override
  public ContextPropagators getPropagators() {
    return delegate.getPropagators();
  }
}
