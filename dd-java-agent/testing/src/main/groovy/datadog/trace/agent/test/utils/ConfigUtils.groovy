package datadog.trace.agent.test.utils

import datadog.trace.api.Config
import lombok.SneakyThrows
import net.bytebuddy.agent.ByteBuddyAgent
import net.bytebuddy.agent.builder.AgentBuilder
import net.bytebuddy.dynamic.ClassFileLocator
import net.bytebuddy.dynamic.Transformer

import java.lang.reflect.Modifier
import java.util.concurrent.Callable

import static net.bytebuddy.description.modifier.FieldManifestation.VOLATILE
import static net.bytebuddy.description.modifier.Ownership.STATIC
import static net.bytebuddy.description.modifier.Visibility.PUBLIC
import static net.bytebuddy.matcher.ElementMatchers.named
import static net.bytebuddy.matcher.ElementMatchers.none

class ConfigUtils {
  private static class ConfigInstance {
    // Wrapped in a static class to lazy load.
    static final FIELD = Config.getDeclaredField("INSTANCE")
    static final RUNTIME_ID_FIELD = Config.getDeclaredField("runtimeId")
  }

  @SneakyThrows
  synchronized static <T extends Object> Object withConfigOverride(final String name, final String value, final Callable<T> r) {
    // Ensure the class was retransformed properly in AgentTestRunner.makeConfigInstanceModifiable()
    assert Modifier.isPublic(ConfigInstance.FIELD.getModifiers())
    assert Modifier.isStatic(ConfigInstance.FIELD.getModifiers())
    assert Modifier.isVolatile(ConfigInstance.FIELD.getModifiers())
    assert !Modifier.isFinal(ConfigInstance.FIELD.getModifiers())

    def existingConfig = Config.get()
    Properties properties = new Properties()
    properties.put(name, value)
    ConfigInstance.FIELD.set(null, new Config(properties, existingConfig))
    assert Config.get() != existingConfig
    try {
      return r.call()
    } finally {
      ConfigInstance.FIELD.set(null, existingConfig)
    }
  }

  /**
   * Provides an callback to set up the testing environment and reset the global configuration after system properties and envs are set.
   *
   * @param r
   * @return
   */
  static updateConfig(final Callable r) {
    makeConfigInstanceModifiable()
    r.call()
    resetConfig()
  }

  /**
   * Reset the global configuration. Please note that Runtime ID is preserved to the pre-existing value.
   */
  static void resetConfig() {
    // Ensure the class was re-transformed properly in AgentTestRunner.makeConfigInstanceModifiable()
    assert Modifier.isPublic(ConfigInstance.FIELD.getModifiers())
    assert Modifier.isStatic(ConfigInstance.FIELD.getModifiers())
    assert Modifier.isVolatile(ConfigInstance.FIELD.getModifiers())
    assert !Modifier.isFinal(ConfigInstance.FIELD.getModifiers())

    assert Modifier.isPublic(ConfigInstance.RUNTIME_ID_FIELD.getModifiers())
    assert !Modifier.isStatic(ConfigInstance.RUNTIME_ID_FIELD.getModifiers())
    assert Modifier.isVolatile(ConfigInstance.RUNTIME_ID_FIELD.getModifiers())
    assert !Modifier.isFinal(ConfigInstance.RUNTIME_ID_FIELD.getModifiers())

    def previousConfig = ConfigInstance.FIELD.get(null)
    def newConfig = new Config()
    ConfigInstance.FIELD.set(null, newConfig)
    if (previousConfig != null) {
      ConfigInstance.RUNTIME_ID_FIELD.set(newConfig, ConfigInstance.RUNTIME_ID_FIELD.get(previousConfig))
    }
  }

  // Keep track of config instance already made modifiable
  private static isConfigInstanceModifiable = false

  static void makeConfigInstanceModifiable() {
    if (isConfigInstanceModifiable) {
      return
    }

    def instrumentation = ByteBuddyAgent.install()
    final transformer =
      new AgentBuilder.Default()
        .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
        .with(AgentBuilder.RedefinitionStrategy.Listener.ErrorEscalating.FAIL_FAST)
        // Config is injected into the bootstrap, so we need to provide a locator.
        .with(
          new AgentBuilder.LocationStrategy.Simple(
            ClassFileLocator.ForClassLoader.ofSystemLoader()))
        .ignore(none()) // Allow transforming bootstrap classes
        .type(named("datadog.trace.api.Config"))
        .transform { builder, typeDescription, classLoader, module ->
          builder
            .field(named("INSTANCE"))
            .transform(Transformer.ForField.withModifiers(PUBLIC, STATIC, VOLATILE))
        }
        // Making runtimeId modifiable so that it can be preserved when resetting config in tests
        .transform { builder, typeDescription, classLoader, module ->
          builder
            .field(named("runtimeId"))
            .transform(Transformer.ForField.withModifiers(PUBLIC, VOLATILE))
        }
        .installOn(instrumentation)
    isConfigInstanceModifiable = true

    final field = ConfigInstance.FIELD
    assert Modifier.isPublic(field.getModifiers())
    assert Modifier.isStatic(field.getModifiers())
    assert Modifier.isVolatile(field.getModifiers())
    assert !Modifier.isFinal(field.getModifiers())

    final runtimeIdField = ConfigInstance.RUNTIME_ID_FIELD
    assert Modifier.isPublic(runtimeIdField.getModifiers())
    assert !Modifier.isStatic(ConfigInstance.RUNTIME_ID_FIELD.getModifiers())
    assert Modifier.isVolatile(runtimeIdField.getModifiers())
    assert !Modifier.isFinal(runtimeIdField.getModifiers())

    // No longer needed (Unless class gets retransformed somehow).
    instrumentation.removeTransformer(transformer)
  }
}
