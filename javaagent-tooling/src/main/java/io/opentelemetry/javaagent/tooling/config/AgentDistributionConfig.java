/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.config;

import static io.opentelemetry.api.incubator.config.DeclarativeConfigProperties.empty;

import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.api.incubator.config.internal.ExtendedDeclarativeConfigProperties;
import io.opentelemetry.instrumentation.api.internal.Initializer;

/**
 * Javaagent distribution-specific configuration.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public class AgentDistributionConfig {
  static ExtendedDeclarativeConfigProperties distributionConfig =
      new ExtendedDeclarativeConfigProperties(empty());

  public static ExtendedDeclarativeConfigProperties get() {
    return distributionConfig;
  }

  @Initializer
  public static void set(DeclarativeConfigProperties distributionConfig) {
    AgentDistributionConfig.distributionConfig =
        new ExtendedDeclarativeConfigProperties(distributionConfig);
  }

  private AgentDistributionConfig() {}
}
