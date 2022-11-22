/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.extension.ignore;

import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.Ordered;

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
   */
  void configure(IgnoredTypesBuilder builder, ConfigProperties config);
}
