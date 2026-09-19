/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachecommonspool.v2_0;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.instrumentation.apachecommonspool.v2_0.CommonsPoolSingletons.telemetry;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.bootstrap.apachecommonspool.CommonsPoolMetricsSuppression;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.commons.pool2.impl.BaseGenericObjectPool;
import org.apache.commons.pool2.impl.BaseObjectPoolConfig;
import org.apache.commons.pool2.impl.GenericKeyedObjectPoolMXBean;
import org.apache.commons.pool2.impl.GenericObjectPoolMXBean;

class GenericObjectPoolInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return namedOneOf(
        "org.apache.commons.pool2.impl.GenericObjectPool",
        "org.apache.commons.pool2.impl.GenericKeyedObjectPool");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isConstructor()
            .and(takesArguments(2))
            .and(
                takesArgument(
                    1,
                    namedOneOf(
                        "org.apache.commons.pool2.impl.GenericObjectPoolConfig",
                        "org.apache.commons.pool2.impl.GenericKeyedObjectPoolConfig"))),
        getClass().getName() + "$ConstructorAdvice");

    transformer.applyAdviceToMethod(
        named("close").and(takesArguments(0)), getClass().getName() + "$CloseAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConstructorAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(
        @Advice.This BaseGenericObjectPool<?> pool,
        @Advice.Argument(1) BaseObjectPoolConfig config) {
      if (CommonsPoolMetricsSuppression.isSuppressed(currentContext())) {
        return;
      }

      String poolName = config.getJmxNamePrefix();
      if (poolName == null || poolName.isEmpty()) {
        poolName = "unknown";
      }

      if (pool instanceof GenericKeyedObjectPoolMXBean<?>) {
        telemetry().registerMetrics((GenericKeyedObjectPoolMXBean<?>) pool, "keyed-" + poolName);
      } else {
        telemetry().registerMetrics((GenericObjectPoolMXBean) pool, poolName);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class CloseAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void onExit(@Advice.This BaseGenericObjectPool<?> pool) {
      if (pool instanceof GenericKeyedObjectPoolMXBean<?>) {
        telemetry().unregisterMetrics((GenericKeyedObjectPoolMXBean<?>) pool);
      } else {
        telemetry().unregisterMetrics((GenericObjectPoolMXBean) pool);
      }
    }
  }
}
