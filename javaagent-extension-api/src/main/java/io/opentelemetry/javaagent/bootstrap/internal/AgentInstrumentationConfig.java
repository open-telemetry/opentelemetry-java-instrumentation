/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.internal;

import static java.util.Objects.requireNonNull;

import io.opentelemetry.instrumentation.api.incubator.config.internal.InstrumentationConfig;
import java.util.logging.Logger;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class AgentInstrumentationConfig {
  private AgentInstrumentationConfig() {}

  private static final Logger logger = Logger.getLogger(AgentInstrumentationConfig.class.getName());

  private static final InstrumentationConfig DEFAULT = new EmptyInstrumentationConfig();

  // lazy initialized, so that javaagent can set it
  private static volatile InstrumentationConfig instance = DEFAULT;

  /**
   * Sets the instrumentation configuration singleton. This method is only supposed to be called
   * once, during the agent initialization, just before {@link AgentInstrumentationConfig#get()} is
   * used for the first time.
   *
   * <p>This method is internal and is hence not for public use. Its API is unstable and can change
   * at any time.
   */
  public static void internalInitializeConfig(InstrumentationConfig config) {
    if (instance != DEFAULT) {
      logger.warning("InstrumentationConfig#instance was already set earlier");
      return;
    }
    instance = requireNonNull(config);
  }

  /** Returns the global instrumentation configuration. */
  public static InstrumentationConfig get() {
    return instance;
  }
}
