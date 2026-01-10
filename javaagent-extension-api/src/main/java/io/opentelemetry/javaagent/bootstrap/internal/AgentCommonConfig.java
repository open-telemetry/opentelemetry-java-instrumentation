/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.internal;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.api.incubator.config.EnabledInstrumentations;
import io.opentelemetry.instrumentation.api.incubator.config.internal.CommonConfig;
import io.opentelemetry.instrumentation.api.incubator.config.internal.ExtendedDeclarativeConfigProperties;
import io.opentelemetry.instrumentation.api.internal.Initializer;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class AgentCommonConfig {
  private AgentCommonConfig() {}

  private static final CommonConfig instance = new CommonConfig(GlobalOpenTelemetry.get());

  private static EnabledInstrumentations enabledInstrumentations;

  private static ExtendedDeclarativeConfigProperties distributionConfig;

  public static CommonConfig get() {
    return instance;
  }

  public static EnabledInstrumentations getEnabledInstrumentations() {
    return enabledInstrumentations;
  }

  @Initializer
  public static void setEnabledInstrumentations(EnabledInstrumentations enabledInstrumentations) {
    AgentCommonConfig.enabledInstrumentations = enabledInstrumentations;
  }

  public static ExtendedDeclarativeConfigProperties getDistributionConfig() {
    return distributionConfig;
  }

  @Initializer
  public static void setDistributionConfig(DeclarativeConfigProperties distributionConfig) {
    AgentCommonConfig.distributionConfig =
        new ExtendedDeclarativeConfigProperties(distributionConfig);
  }
}
