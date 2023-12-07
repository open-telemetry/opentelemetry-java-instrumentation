/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.v4_0.redis;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.vertx.redis.client.RedisConnection;
import io.vertx.redis.client.impl.RedisStandaloneConnection;
import io.vertx.redis.client.impl.RedisURI;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class RedisConnectionProviderInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("io.vertx.redis.client.impl.RedisConnectionManager$RedisConnectionProvider");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    // 4.1.0
    transformer.applyAdviceToMethod(
        named("init").and(not(takesArgument(0, named("io.vertx.redis.client.RedisConnection")))),
        this.getClass().getName() + "$InitAdvice");
    // 4.0.0
    transformer.applyAdviceToMethod(
        named("init").and(takesArgument(0, named("io.vertx.redis.client.RedisConnection"))),
        this.getClass().getName() + "$InitWithConnectionAdvice");
  }

  @SuppressWarnings("unused")
  public static class InitAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(@Advice.FieldValue("redisURI") RedisURI redisUri) {
      // for 4.1.0 and later we set RedisURI in a ThreadLocal that is used in advice added in
      // RedisStandaloneConnectionInstrumentation that attaches RedisURI to
      // RedisStandaloneConnection
      VertxRedisClientSingletons.setRedisUriThreadLocal(redisUri);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit() {
      VertxRedisClientSingletons.setRedisUriThreadLocal(null);
    }
  }

  @SuppressWarnings("unused")
  public static class InitWithConnectionAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(0) RedisConnection connection,
        @Advice.FieldValue("redisURI") RedisURI redisUri) {
      // for 4.0.x we don't need to use ThreadLocal like in 4.1.0 because in this method we have
      // access to both the RedisURI and RedisConnection
      if (connection instanceof RedisStandaloneConnection) {
        VertxRedisClientSingletons.setRedisUri((RedisStandaloneConnection) connection, redisUri);
      }
    }
  }
}
