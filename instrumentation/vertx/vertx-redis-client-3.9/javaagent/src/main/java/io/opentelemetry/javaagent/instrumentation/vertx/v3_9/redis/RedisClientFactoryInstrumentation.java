/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.v3_9.redis;

import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.vertx.redis.client.RedisOptions;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class RedisClientFactoryInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("io.vertx.redis.client.Redis");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    // Instrument createClient methods to capture connection configuration
    transformer.applyAdviceToMethod(
        isMethod()
            .and(isStatic())
            .and(named("createClient"))
            .and(takesArgument(1, named("io.vertx.redis.client.RedisOptions"))),
        this.getClass().getName() + "$CreateClientAdvice");
  }

  @SuppressWarnings("unused")
  public static class CreateClientAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(@Advice.Argument(1) @Nullable RedisOptions options) {
      if (options != null) {
        // Store connection configuration in ThreadLocal for later use
        VertxRedisClientSingletons.setRedisOptions(options);
      }
    }
  }
}
