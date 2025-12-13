/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.config;

import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import javax.annotation.Nullable;

public final class RuntimeConfigProperties {

  // TODO populate with empty config for safety?
  @Nullable private static volatile ConfigProperties instance;

  public static void set(ConfigProperties configProperties) {
    instance = configProperties;
  }

  @Nullable
  public static ConfigProperties get() {
    return instance;
  }

  private RuntimeConfigProperties() {}
}
