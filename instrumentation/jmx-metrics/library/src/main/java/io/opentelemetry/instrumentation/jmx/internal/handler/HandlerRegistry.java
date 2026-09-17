/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.internal.handler;

import io.opentelemetry.common.ComponentLoader;
import io.opentelemetry.instrumentation.jmx.internal.ExperimentalJmxMetricHandler;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class HandlerRegistry {

  private static final Logger logger = Logger.getLogger(HandlerRegistry.class.getName());

  private final Map<String, ExperimentalJmxMetricHandler> handlers = new HashMap<>();

  public HandlerRegistry() {}

  /**
   * Loads all available jmx metric handlers
   *
   * @param componentLoader component loader
   */
  public void load(ComponentLoader componentLoader) {
    for (ExperimentalJmxMetricHandler handler :
        componentLoader.load(ExperimentalJmxMetricHandler.class)) {

      String name = handler.getName();
      if (handlers.putIfAbsent(name, handler) != null) {
        logger.warning(
            "Multiple JmxMetricHandlers with the same name found: "
                + name
                + ". Only one will be used.");
      }
    }
  }

  @Nullable
  public ExperimentalJmxMetricHandler getHandler(String name) {
    return handlers.get(name);
  }
}
