/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.internal;

import static java.util.Objects.requireNonNull;

import io.opentelemetry.instrumentation.api.incubator.internal.config.CoreInstrumentationConfig;
import java.util.logging.Logger;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class InstrumentationConfig {
  private InstrumentationConfig() {}

  private static final Logger logger = Logger.getLogger(InstrumentationConfig.class.getName());

  private static final CoreInstrumentationConfig DEFAULT = new EmptyInstrumentationConfig();

  // lazy initialized, so that javaagent can set it
  private static volatile CoreInstrumentationConfig instance = DEFAULT;

  /**
   * Sets the instrumentation configuration singleton. This method is only supposed to be called
   * once, during the agent initialization, just before {@link InstrumentationConfig#get()} is used
   * for the first time.
   *
   * <p>This method is internal and is hence not for public use. Its API is unstable and can change
   * at any time.
   */
  public static void internalInitializeConfig(CoreInstrumentationConfig config) {
    if (instance != DEFAULT) {
      logger.warning("InstrumentationConfig#instance was already set earlier");
      return;
    }
    instance = requireNonNull(config);
  }

  /** Returns the global instrumentation configuration. */
  public static CoreInstrumentationConfig get() {
    return instance;
  }
}
