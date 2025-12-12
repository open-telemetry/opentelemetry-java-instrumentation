/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.properties;

import io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import io.opentelemetry.api.incubator.config.ConfigProvider;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import java.io.Closeable;
import javax.annotation.concurrent.ThreadSafe;

/**
 * An {@link OpenTelemetrySdk} that also implements {@link ExtendedOpenTelemetry} and provides a
 * {@link ConfigProvider}.
 *
 * <p>This allows instrumentations to use {@code ExtendedOpenTelemetry.getConfigProvider()}
 * regardless of whether the SDK was configured with system properties or YAML.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
@ThreadSafe
public final class SpringOpenTelemetrySdk extends OpenTelemetrySdk
    implements ExtendedOpenTelemetry, Closeable {

  private final ConfigProvider configProvider;

  private SpringOpenTelemetrySdk(OpenTelemetrySdk delegate, ConfigProvider configProvider) {
    super(
        delegate.getSdkTracerProvider(),
        delegate.getSdkMeterProvider(),
        delegate.getSdkLoggerProvider(),
        delegate.getPropagators());
    this.configProvider = configProvider;
  }

  /**
   * Creates a new {@link SpringOpenTelemetrySdk} that wraps the given SDK and provides the given
   * ConfigProvider.
   *
   * @param openTelemetrySdk the SDK to wrap
   * @param configProvider the ConfigProvider to return from getConfigProvider()
   * @return a new SpringOpenTelemetrySdk
   */
  public static SpringOpenTelemetrySdk create(
      OpenTelemetrySdk openTelemetrySdk, ConfigProvider configProvider) {
    return new SpringOpenTelemetrySdk(openTelemetrySdk, configProvider);
  }

  @Override
  public ConfigProvider getConfigProvider() {
    return configProvider;
  }

  @Override
  public String toString() {
    return "SpringOpenTelemetrySdk{"
        + "tracerProvider="
        + getSdkTracerProvider()
        + ", meterProvider="
        + getSdkMeterProvider()
        + ", loggerProvider="
        + getSdkLoggerProvider()
        + ", propagators="
        + getPropagators()
        + ", configProvider="
        + configProvider
        + '}';
  }
}
