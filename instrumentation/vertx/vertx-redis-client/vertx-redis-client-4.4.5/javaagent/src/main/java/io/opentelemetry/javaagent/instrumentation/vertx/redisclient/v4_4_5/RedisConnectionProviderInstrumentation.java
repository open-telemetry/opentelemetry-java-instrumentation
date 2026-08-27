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
    // 4.4.5 through 4.x, where the provider is built with the connect options; 5.x passes a
    // supplier of them instead, so its constructor does not match here
    transformer.applyAdviceToMethod(
        isConstructor().and(takesArgument(4, named("io.vertx.redis.client.RedisConnectOptions"))),
        getClass().getName() + "$ConstructorAdvice");
    // 5.0.0 and later, where the connect options are resolved per connection; earlier versions take
    // a NetSocket in this position, so their init does not match here
    transformer.applyAdviceToMethod(
        named("init").and(takesArgument(1, named("io.vertx.redis.client.RedisConnectOptions"))),
        getClass().getName() + "$InitAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConstructorAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Argument(4) RedisConnectOptions options,
        @Advice.FieldValue("redisURI") RedisURI redisUri) {
      VertxRedisServerTargets.set(redisUri, VertxRedisServerTargets.of(options));
    }
  }

  @SuppressWarnings("unused")
  public static class InitAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static void onEnter(@Advice.Argument(1) RedisConnectOptions options) {
      VertxRedisServerTargets.setCurrent(VertxRedisServerTargets.of(options));
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit() {
      VertxRedisServerTargets.clearCurrent();
    }
  }
}
