/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.auto.test.utils

import io.opentelemetry.auto.test.AgentTestRunner
import io.opentelemetry.instrumentation.api.config.Config
import io.opentelemetry.javaagent.tooling.config.AgentConfigBuilder
import io.opentelemetry.javaagent.tooling.config.ConfigInitializer
import java.util.concurrent.Callable

class ConfigUtils {

  synchronized static <T extends Object> Object withConfigOverride(final String name, final String value, final Callable<T> r) {
    try {
      def existingConfig = Config.get()
      Properties properties = new Properties()
      properties.put(name, value)
      setConfig(new AgentConfigBuilder()
        .readProperties(existingConfig.asJavaProperties())
        .readProperties(properties)
        .build())
      assert Config.get() != existingConfig
      try {
        return r.call()
      } finally {
        setConfig(existingConfig)
      }
    } catch (Throwable t) {
      throw ExceptionUtils.sneakyThrow(t)
    }
  }

  /**
   * Provides an callback to set up the testing environment and reset the global configuration after system properties and envs are set.
   */
  static updateConfig(final Callable r) {
    r.call()
    resetConfig()
    AgentTestRunner.resetInstrumentation()
  }

  /**
   * Reset the global configuration. Please note that Runtime ID is preserved to the pre-existing value.
   */
  static void resetConfig() {
    setConfig(Config.DEFAULT)
    ConfigInitializer.initialize()
  }

  private static setConfig(Config config) {
    Config.INSTANCE = config
  }
}
