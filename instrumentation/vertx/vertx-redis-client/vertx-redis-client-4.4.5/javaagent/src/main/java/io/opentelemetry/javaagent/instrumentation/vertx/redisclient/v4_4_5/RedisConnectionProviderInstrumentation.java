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
import io.vertx.redis.client.impl.RedisURI;
import java.util.function.Supplier;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class RedisConnectionProviderInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("io.vertx.redis.client.impl.RedisConnectionManager$RedisConnectionProvider");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isConstructor().and(takesArgument(4, named("io.vertx.redis.client.RedisConnectOptions"))),
        getClass().getName() + "$ConstructorWithOptionsAdvice");
    transformer.applyAdviceToMethod(
        isConstructor()
            .and(takesArgument(4, named("java.util.function.Supplier")))
            .and(takesArgument(6, String.class)),
        getClass().getName() + "$ConstructorWithEndpointAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConstructorWithOptionsAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Argument(4) RedisConnectOptions options,
        @Advice.FieldValue("redisURI") RedisURI redisUri) {
      VertxRedisServerTargets.set(redisUri, VertxRedisServerTargets.get(options));
    }
  }

  @SuppressWarnings("unused")
  public static class ConstructorWithEndpointAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Argument(4) Supplier<?> optionsSupplier,
        @Advice.FieldValue("redisURI") RedisURI redisUri) {
      VertxRedisServerTargets.set(redisUri, VertxRedisServerTargets.get(optionsSupplier));
    }
  }
}
