package datadog.trace.agent.test.utils

import datadog.trace.api.Config
import datadog.trace.context.TraceScope
import io.opentracing.Scope
import io.opentracing.Span
import io.opentracing.tag.Tags
import io.opentracing.util.GlobalTracer
import lombok.SneakyThrows

import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.util.concurrent.Callable

import static io.opentracing.log.Fields.ERROR_OBJECT

class TraceUtils {

  @SneakyThrows
  static <T extends Object> Object runUnderTrace(final String rootOperationName, final Callable<T> r) {
    final Scope scope = GlobalTracer.get().buildSpan(rootOperationName).startActive(true)
    ((TraceScope) scope).setAsyncPropagation(true)

    try {
      return r.call()
    } catch (final Exception e) {
      final Span span = scope.span()
      Tags.ERROR.set(span, true)
      span.log(Collections.singletonMap(ERROR_OBJECT, e))

      throw e
    } finally {
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
