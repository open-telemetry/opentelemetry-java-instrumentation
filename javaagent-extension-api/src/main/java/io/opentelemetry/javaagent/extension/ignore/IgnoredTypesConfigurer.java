/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.extension.ignore;

import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.javaagent.extension.Ordered;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;

/**
 * An {@link IgnoredTypesConfigurer} can be used to augment built-in instrumentation restrictions:
 * ignore some classes and exclude them from being instrumented, or explicitly allow them to be
 * instrumented if the agent ignored them by default.
 *
 * <p>This is a service provider interface that requires implementations to be registered in a
 * provider-configuration file stored in the {@code META-INF/services} resource directory.
 */
public interface IgnoredTypesConfigurer extends Ordered {

  /**
   * Configure the passed {@code builder} and define which classes should be ignored when
   * instrumenting.
   *
   * @deprecated Use {@link #configure(IgnoredTypesBuilder, ConfigProperties)} instead.
   */
  @Deprecated
  default void configure(Config config, IgnoredTypesBuilder builder) {
    throw new UnsupportedOperationException(
        "This method is deprecated and will be removed in a future release;"
            + " implement IgnoredTypesConfigurer#configure(IgnoredTypesBuilder, ConfigProperties) instead");
  }

  /**
   * Configure the passed {@code builder} and define which classes should be ignored when
   * instrumenting.
   */
  default void configure(IgnoredTypesBuilder builder, ConfigProperties config) {
    configure(Config.get(), builder);
  }
}
