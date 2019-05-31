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
  }

  // TODO: ideally all users of this should switch to using Config object (and withConfigOverride) instead.
  @Deprecated
  @SneakyThrows
  static <T extends Object> Object withSystemProperty(final String name, final String value, final Callable<T> r) {
    if (value == null) {
      System.clearProperty(name)
    } else {
      System.setProperty(name, value)
    }
    try {
      return r.call()
    } finally {
      System.clearProperty(name)
    }
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
   * Calling will reset the runtimeId too, so it might cause problems around runtimeId verification.
   */
  static void resetConfig() {
    // Ensure the class was retransformed properly in AgentTestRunner.makeConfigInstanceModifiable()
    assert Modifier.isPublic(ConfigInstance.FIELD.getModifiers())
    assert Modifier.isStatic(ConfigInstance.FIELD.getModifiers())
    assert Modifier.isVolatile(ConfigInstance.FIELD.getModifiers())
    assert !Modifier.isFinal(ConfigInstance.FIELD.getModifiers())

    ConfigInstance.FIELD.set(null, new Config())
  }

  static void makeConfigInstanceModifiable() {
    def instrumentation = ByteBuddyAgent.install()
    final transformer =
      new AgentBuilder.Default()
        .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
        .with(AgentBuilder.RedefinitionStrategy.Listener.ErrorEscalating.FAIL_FAST)
      // Config is injected into the bootstrap, so we need to provide a locator.
        .with(
          new AgentBuilder.LocationStrategy.Simple(
            ClassFileLocator.ForClassLoader.ofSystemLoader()))
        .ignore(none()) // Allow transforming boostrap classes
        .type(named("datadog.trace.api.Config"))
        .transform { builder, typeDescription, classLoader, module ->
          builder
            .field(named("INSTANCE"))
            .transform(Transformer.ForField.withModifiers(PUBLIC, STATIC, VOLATILE))
        }
        .installOn(instrumentation)

    final field = ConfigInstance.FIELD
    assert Modifier.isPublic(field.getModifiers())
    assert Modifier.isStatic(field.getModifiers())
    assert Modifier.isVolatile(field.getModifiers())
    assert !Modifier.isFinal(field.getModifiers())

    // No longer needed (Unless class gets retransformed somehow).
    instrumentation.removeTransformer(transformer)
  }
}
