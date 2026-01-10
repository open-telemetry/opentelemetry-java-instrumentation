/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.config;

import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import javax.annotation.Nullable;

public class JavaagentDistribution {
  @Nullable private static DeclarativeConfigProperties instance;

  /**
   * Returns the declarative config properties used to configure the javaagent, or null if no
   * declarative config was used.
   */
  @Nullable
  public static DeclarativeConfigProperties get() {
    return instance;
  }

  public static void set(DeclarativeConfigProperties properties) {
    JavaagentDistribution.instance = properties;
  }

  private JavaagentDistribution() {}
}
