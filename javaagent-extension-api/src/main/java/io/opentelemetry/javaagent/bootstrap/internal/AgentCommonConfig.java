/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.internal;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.CommonConfig;
import java.util.function.Function;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class AgentCommonConfig {
  private AgentCommonConfig() {}

  private static final CommonConfig instance = new CommonConfig(GlobalOpenTelemetry.get());

  private static boolean isDefaultEnabled = true;

  private static Function<String, Boolean> isModuleEnabledExplicitly = moduleName -> null;

  public static CommonConfig get() {
    return instance;
  }

  public static boolean isDefaultEnabled() {
    return isDefaultEnabled;
  }

  public static void setIsDefaultEnabled(boolean isDefaultEnabled) {
    AgentCommonConfig.isDefaultEnabled = isDefaultEnabled;
  }

  public static void setIsModuleEnabledExplicitly(
      Function<String, Boolean> isModuleEnabledExplicitly) {
    AgentCommonConfig.isModuleEnabledExplicitly = isModuleEnabledExplicitly;
  }

  public static Boolean isModuleEnabledExplicitly(String moduleName) {
    return isModuleEnabledExplicitly.apply(moduleName);
  }
}
