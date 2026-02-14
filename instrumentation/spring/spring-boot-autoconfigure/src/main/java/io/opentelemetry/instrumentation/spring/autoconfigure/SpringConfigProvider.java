/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure;

import io.opentelemetry.api.incubator.config.ConfigProvider;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;

/**
 * Wraps an existing {@link ConfigProvider} (e.g. the one produced by {@link
 * io.opentelemetry.sdk.extension.incubator.fileconfig.DeclarativeConfiguration}) and decorates its
 * {@link DeclarativeConfigProperties} with type coercion, because Spring doesn't preserve the
 * original types from YAML.
 */
final class SpringConfigProvider implements ConfigProvider {

  private final DeclarativeConfigProperties instrumentationConfig;

  private SpringConfigProvider(ConfigProvider delegate) {
    DeclarativeConfigProperties original = delegate.getInstrumentationConfig();
    this.instrumentationConfig =
        original != null
            ? CoercingDeclarativeConfigProperties.wrap(original)
            : DeclarativeConfigProperties.empty();
  }

  /**
   * Create a {@link SpringConfigProvider} that wraps the given {@code delegate}, adding type
   * coercion for Spring environments.
   *
   * @param delegate the config provider produced by the SDK's declarative configuration
   * @return the {@link SpringConfigProvider}
   */
  static SpringConfigProvider create(ConfigProvider delegate) {
    return new SpringConfigProvider(delegate);
  }

  @Override
  public DeclarativeConfigProperties getInstrumentationConfig() {
    return instrumentationConfig;
  }

  @Override
  public String toString() {
    return "SpringConfigProvider{instrumentationConfig=" + instrumentationConfig + '}';
  }
}
