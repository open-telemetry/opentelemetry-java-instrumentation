package datadog.trace.util.test

import net.bytebuddy.agent.ByteBuddyAgent
import net.bytebuddy.agent.builder.AgentBuilder
import net.bytebuddy.dynamic.ClassFileLocator
import net.bytebuddy.dynamic.Transformer
import spock.lang.Specification

import java.lang.reflect.Modifier

import static net.bytebuddy.description.modifier.FieldManifestation.VOLATILE
import static net.bytebuddy.description.modifier.Ownership.STATIC
import static net.bytebuddy.description.modifier.Visibility.PUBLIC
import static net.bytebuddy.matcher.ElementMatchers.named
import static net.bytebuddy.matcher.ElementMatchers.none

abstract class DDSpecification extends Specification {
  private static final String CONFIG = "datadog.trace.api.Config"

  static class ConfigInstance {
    // Wrapped in a static class to lazy load.
    static final CONFIG_INSTANCE_FIELD = Class.forName(CONFIG).getDeclaredField("INSTANCE")
    static final RUNTIME_ID_FIELD = Class.forName(CONFIG).getDeclaredField("runtimeId")
  }

  static {
    makeConfigInstanceModifiable()
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
        .type(named(CONFIG))
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

    final field = ConfigInstance.CONFIG_INSTANCE_FIELD
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
