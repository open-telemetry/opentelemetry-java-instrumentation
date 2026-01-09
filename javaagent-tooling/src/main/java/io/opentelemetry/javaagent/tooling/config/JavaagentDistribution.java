/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.config;

import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import java.util.List;
import javax.annotation.Nullable;

public class JavaagentDistribution {
  @Nullable private static DeclarativeConfigProperties instance;
  // Should not be parsed for each call
  @Nullable private static List<String> enabledModules;
  @Nullable private static List<String> disabledModules;

  /**
   * @return the declarative config properties used to configure the javaagent, or null if no
   *     declarative config was used
   */
  @Nullable
  public static DeclarativeConfigProperties get() {
    return instance;
  }

  public static void set(DeclarativeConfigProperties properties) {
    JavaagentDistribution.instance = properties;

    DeclarativeConfigProperties instrumentation = properties.getStructured("instrumentation");
    if (instrumentation != null) {
      disabledModules = instrumentation.getScalarList("disabled", String.class);
      enabledModules = instrumentation.getScalarList("enabled", String.class);
    }
  }

  @Nullable
  public static List<String> getEnabledModules() {
    return enabledModules;
  }

  @Nullable
  public static List<String> getDisabledModules() {
    return disabledModules;
  }

  private JavaagentDistribution() {}
}
