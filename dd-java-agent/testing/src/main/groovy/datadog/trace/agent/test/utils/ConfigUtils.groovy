package datadog.trace.agent.test.utils

import datadog.trace.api.Config
import lombok.SneakyThrows

import java.lang.reflect.Modifier
import java.util.concurrent.Callable

class ConfigUtils {

  static final CONFIG_INSTANCE_FIELD = Config.getDeclaredField("INSTANCE")
  static final RUNTIME_ID_FIELD = Config.getDeclaredField("runtimeId")

  @SneakyThrows
  synchronized static <T extends Object> Object withConfigOverride(final String name, final String value, final Callable<T> r) {
    // Ensure the class was retransformed properly in DDSpecification.makeConfigInstanceModifiable()
    assert Modifier.isPublic(CONFIG_INSTANCE_FIELD.getModifiers())
    assert Modifier.isStatic(CONFIG_INSTANCE_FIELD.getModifiers())
    assert Modifier.isVolatile(CONFIG_INSTANCE_FIELD.getModifiers())
    assert !Modifier.isFinal(CONFIG_INSTANCE_FIELD.getModifiers())

    def existingConfig = Config.get()
    Properties properties = new Properties()
    properties.put(name, value)
    CONFIG_INSTANCE_FIELD.set(null, new Config(properties, existingConfig))
    assert Config.get() != existingConfig
    try {
      return r.call()
    } finally {
      CONFIG_INSTANCE_FIELD.set(null, existingConfig)
    }
  }

  /**
   * Provides an callback to set up the testing environment and reset the global configuration after system properties and envs are set.
   *
   * @param r
   * @return
   */
  static updateConfig(final Callable r) {
    r.call()
    resetConfig()
  }

  /**
   * Reset the global configuration. Please note that Runtime ID is preserved to the pre-existing value.
   */
  static void resetConfig() {
    // Ensure the class was re-transformed properly in DDSpecification.makeConfigInstanceModifiable()
    assert Modifier.isPublic(CONFIG_INSTANCE_FIELD.getModifiers())
    assert Modifier.isStatic(CONFIG_INSTANCE_FIELD.getModifiers())
    assert Modifier.isVolatile(CONFIG_INSTANCE_FIELD.getModifiers())
    assert !Modifier.isFinal(CONFIG_INSTANCE_FIELD.getModifiers())

    assert Modifier.isPublic(RUNTIME_ID_FIELD.getModifiers())
    assert !Modifier.isStatic(RUNTIME_ID_FIELD.getModifiers())
    assert Modifier.isVolatile(RUNTIME_ID_FIELD.getModifiers())
    assert !Modifier.isFinal(RUNTIME_ID_FIELD.getModifiers())

    def previousConfig = CONFIG_INSTANCE_FIELD.get(null)
    def newConfig = new Config()
    CONFIG_INSTANCE_FIELD.set(null, newConfig)
    if (previousConfig != null) {
      RUNTIME_ID_FIELD.set(newConfig, RUNTIME_ID_FIELD.get(previousConfig))
    }
  }
}
