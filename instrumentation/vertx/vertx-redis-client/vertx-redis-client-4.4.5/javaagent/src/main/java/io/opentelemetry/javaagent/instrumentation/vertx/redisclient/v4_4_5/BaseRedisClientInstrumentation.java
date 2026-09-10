/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.redisclient.v4_4_5;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.vertx.redis.client.RedisConnectOptions;
import io.vertx.redis.client.impl.RedisConnectionManagerUtil;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class BaseRedisClientInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("io.vertx.redis.client.impl.BaseRedisClient");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isConstructor().and(takesArgument(3, named("io.vertx.redis.client.RedisConnectOptions"))),
        getClass().getName() + "$ConstructorWithOptionsAdvice");
    transformer.applyAdviceToMethod(
        isConstructor().and(takesArgument(3, named("java.util.function.Supplier"))),
        getClass().getName() + "$ConstructorWithSupplierAdvice");
    transformer.applyAdviceToMethod(named("close"), getClass().getName() + "$CloseAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConstructorWithOptionsAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Argument(3) RedisConnectOptions options,
        @Advice.FieldValue("connectionManager") Object manager) {
      RedisConnectionManagerUtil.setServerTarget(manager, VertxRedisServerTargets.of(options));
    }
  }

  @SuppressWarnings("unused")
  public static class ConstructorWithSupplierAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.FieldValue("connectionManager") Object manager) {
      RedisConnectionManagerUtil.setServerTarget(
          manager, VertxRedisServerTargets.getFactoryTarget());
    }
  }

  @SuppressWarnings("unused")
  public static class CloseAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.FieldValue("connectionManager") Object manager) {
      RedisConnectionManagerUtil.setServerTarget(manager, null);
    }
  }
}
