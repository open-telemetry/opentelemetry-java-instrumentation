package datadog.trace.agent.test.utils

import datadog.trace.agent.decorator.BaseDecorator
import datadog.trace.api.Config
import datadog.trace.context.TraceScope
import io.opentracing.Scope
import io.opentracing.util.GlobalTracer
import lombok.SneakyThrows

import java.lang.reflect.Modifier
import java.util.concurrent.Callable

class TraceUtils {
  static final def CONFIG_INSTANCE_FIELD = Config.getDeclaredField("INSTANCE")

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
  synchronized static <T extends Object> Object withConfigOverride(final String name, final String value, final Callable<T> r) {
    // Ensure the class was retransformed properly in AgentTestRunner.makeConfigInstanceModifiable()
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
   * Calling will reset the runtimeId too, so it might cause problems around runtimeId verification.
   */
  static void resetConfig() {
    // Ensure the class was retransformed properly in AgentTestRunner.makeConfigInstanceModifiable()
    assert Modifier.isPublic(CONFIG_INSTANCE_FIELD.getModifiers())
    assert Modifier.isStatic(CONFIG_INSTANCE_FIELD.getModifiers())
    assert Modifier.isVolatile(CONFIG_INSTANCE_FIELD.getModifiers())
    assert !Modifier.isFinal(CONFIG_INSTANCE_FIELD.getModifiers())

    CONFIG_INSTANCE_FIELD.set(null, new Config())
  }
}
