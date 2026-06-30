/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachecommonspool2.v2_0;

import static io.opentelemetry.javaagent.instrumentation.apachecommonspool2.v2_0.CommonsPool2Singletons.telemetry;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.management.ObjectName;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.commons.pool2.impl.BaseGenericObjectPool;
import org.apache.commons.pool2.impl.GenericKeyedObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPool;

class BaseGenericObjectPoolInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.commons.pool2.impl.BaseGenericObjectPool");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isConstructor()
            .and(takesArguments(3))
            .and(takesArgument(0, named("org.apache.commons.pool2.impl.BaseObjectPoolConfig"))),
        getClass().getName() + "$ConstructorAdvice");

    transformer.applyAdviceToMethod(
        named("jmxUnregister").and(takesArguments(0)),
        getClass().getName() + "$JmxUnregisterAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConstructorAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(@Advice.This BaseGenericObjectPool<?> pool) {
      ObjectName objectName = pool.getJmxName();

      String type = objectName == null ? null : objectName.getKeyProperty("type");
      if (type == null) {
        if (pool instanceof GenericKeyedObjectPool) {
          type = "GenericKeyedObjectPool";
        } else if (pool instanceof GenericObjectPool) {
          type = "GenericObjectPool";
        } else {
          type = pool.getClass().getSimpleName();
        }
      }

      String name = objectName == null ? null : objectName.getKeyProperty("name");
      if (name == null) {
        name = "pool";
      }

      telemetry().registerMetrics(pool, type + "-" + name);
    }
  }

  @SuppressWarnings("unused")
  public static class JmxUnregisterAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void onExit(@Advice.This BaseGenericObjectPool<?> pool) {
      telemetry().unregisterMetrics(pool);
    }
  }
}
