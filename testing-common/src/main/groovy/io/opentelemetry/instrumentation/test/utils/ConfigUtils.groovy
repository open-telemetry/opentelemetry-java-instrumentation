/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.test.utils

import io.opentelemetry.instrumentation.api.config.Config
import io.opentelemetry.instrumentation.test.AgentTestRunner
import io.opentelemetry.javaagent.testing.common.AgentInstallerAccess
import io.opentelemetry.javaagent.testing.common.ConfigAccess

import java.util.function.Consumer
/**
 * This class provides utility methods for changing {@link Config} values during tests.
 */
class ConfigUtils {

  /**
   * Same as {@link #updateConfig(java.util.function.Consumer)}, but resets the instrumentation
   * afterwards. {@link AgentTestRunner#setupBeforeTests()} will re-apply the instrumentation once
   * again, but this time it'll use the modified config.
   *
   * It is suggested to call this method in a {@code static} block so that it evaluates before
   * {@code @BeforeClass}-annotated methods.
   *
   * @return Previous configuration.
   */
  synchronized static Config updateConfigAndResetInstrumentation(Consumer<Properties> configModifications) {
    def previousConfig = updateConfig(configModifications)
    AgentInstallerAccess.resetInstrumentation()
    return previousConfig
  }

  /**
   * Allows the caller to modify (add property, remove property, etc) currently used configuration
   * and then sets the current {@link Config#INSTANCE} singleton value to the modified config.
   *
   * @return Previous configuration.
   */
  synchronized static Config updateConfig(Consumer<Properties> configModifications) {
    def properties = new Properties()
    ConfigAccess.getConfig().each {properties.put(it.key, it.value)}
    configModifications.accept(properties)

    Map<String, String> map = new HashMap<>()
    properties.each {map.put((String) it.key, (String) it.value)}
    def newConfig = Config.create(map)
    return setConfig(newConfig)
  }

  /**
   * Sets {@link Config#INSTANCE} singleton value.
   *
   * @return Previous configuration.
   */
  synchronized static Config setConfig(Config config) {
    def previous = Config.create(ConfigAccess.getConfig())
    ConfigAccess.setConfig(config.getAllProperties())
    return previous
  }
}
