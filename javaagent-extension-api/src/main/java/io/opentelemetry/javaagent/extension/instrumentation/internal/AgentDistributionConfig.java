/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.extension.instrumentation.internal;

import static io.opentelemetry.api.incubator.config.DeclarativeConfigProperties.empty;

import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.api.internal.Initializer;

/**
 * Javaagent distribution-specific configuration.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public class AgentDistributionConfig {
  static DeclarativeConfigProperties distributionConfig = empty();

  public static DeclarativeConfigProperties get() {
    return distributionConfig;
  }

  @Initializer
  public static void set(DeclarativeConfigProperties distributionConfig) {
    AgentDistributionConfig.distributionConfig = distributionConfig;
  }

  private AgentDistributionConfig() {}
}
