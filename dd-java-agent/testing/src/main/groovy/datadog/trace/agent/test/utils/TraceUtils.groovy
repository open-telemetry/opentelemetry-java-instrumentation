package datadog.trace.agent.test.utils

import datadog.trace.agent.decorator.BaseDecorator
import datadog.trace.api.Config
import datadog.trace.context.TraceScope
import io.opentracing.Scope
import io.opentracing.util.GlobalTracer
import lombok.SneakyThrows

import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.util.concurrent.Callable

class TraceUtils {
  private static final BaseDecorator DECORATOR = new BaseDecorator() {
    protected String[] instrumentationNames() {
      return new String[0]
    }

    protected String spanType() {
      return null
    }

    protected String component() {
//      return "runUnderTrace"
      return null
    }
  }

  @SneakyThrows
  static <T extends Object> Object runUnderTrace(final String rootOperationName, final Callable<T> r) {
    final Scope scope = GlobalTracer.get().buildSpan(rootOperationName).startActive(true)
    DECORATOR.afterStart(scope)
    ((TraceScope) scope).setAsyncPropagation(true)

    try {
      return r.call()
    } catch (final Exception e) {
      DECORATOR.onError(scope, e)
      throw e
    } finally {
      DECORATOR.beforeFinish(scope)
      scope.close()
    }
  }

  // TODO: ideally all users of this should switch to using Config object (and withConfigOverride) instead.
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
  static <T extends Object> Object withConfigOverride(final String name, final String value, final Callable<T> r) {
    def existingConfig = Config.get()  // We can't reference INSTANCE directly or the reflection below will fail.
    Properties properties = new Properties()
    properties.put(name, value)
    setFinalStatic(Config.getDeclaredField("INSTANCE"), new Config(properties, existingConfig))
    try {
      return r.call()
    } finally {
      setFinalStatic(Config.getDeclaredField("INSTANCE"), existingConfig)
    }
  }

  /**
   * Calling will reset the runtimeId too, so it might cause problems around runtimeId verification.
   */
  static void resetConfig() {
    setFinalStatic(Config.getDeclaredField("INSTANCE"), new Config())
  }

  private static void setFinalStatic(final Field field, final Object newValue) throws Exception {
    setFinal(field, null, newValue)
  }

  private static void setFinal(final Field field, final Object instance, final Object newValue) throws Exception {
    field.setAccessible(true)

    final Field modifiersField = Field.getDeclaredField("modifiers")
    modifiersField.setAccessible(true)
    modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL)

    field.set(instance, newValue)
  }
}
