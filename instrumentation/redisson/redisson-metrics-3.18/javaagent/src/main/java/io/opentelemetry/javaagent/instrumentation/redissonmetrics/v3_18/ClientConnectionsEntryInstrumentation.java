/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redissonmetrics.v3_18;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.Collection;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.redisson.client.RedisClient;
import org.redisson.config.MasterSlaveServersConfig;
import org.redisson.misc.AsyncSemaphore;

class ClientConnectionsEntryInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.redisson.connection.ClientConnectionsEntry");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isConstructor()
            .and(takesArguments(7))
            .and(takesArgument(0, named("org.redisson.client.RedisClient")))
            .and(takesArgument(1, int.class))
            .and(takesArgument(2, int.class))
            .and(takesArgument(3, int.class))
            .and(takesArgument(4, int.class))
            .and(takesArgument(5, named("org.redisson.connection.ConnectionManager")))
            .and(takesArgument(6, named("org.redisson.api.NodeType"))),
        getClass().getName() + "$SevenArgumentConstructorAdvice");
    transformer.applyAdviceToMethod(
        isConstructor()
            .and(takesArguments(6))
            .and(takesArgument(0, named("org.redisson.client.RedisClient")))
            .and(takesArgument(1, int.class))
            .and(takesArgument(2, int.class))
            .and(takesArgument(3, named("org.redisson.connection.IdleConnectionWatcher")))
            .and(takesArgument(4, named("org.redisson.api.NodeType")))
            .and(takesArgument(5, named("org.redisson.config.MasterSlaveServersConfig"))),
        getClass().getName() + "$SixArgumentConstructorAdvice");
  }

  @SuppressWarnings("unused")
  public static class SevenArgumentConstructorAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(
        @Advice.Argument(0) RedisClient redisClient,
        @Advice.Argument(1) int regularMinIdle,
        @Advice.Argument(2) int regularMax,
        @Advice.Argument(3) int subscriptionMinIdle,
        @Advice.Argument(4) int subscriptionMax,
        @Advice.FieldValue("freeConnectionsCounter") AsyncSemaphore regularSemaphore,
        @Advice.FieldValue("freeConnections") Collection<?> regularFreeConnections,
        @Advice.FieldValue("freeSubscribeConnectionsCounter") AsyncSemaphore subscriptionSemaphore,
        @Advice.FieldValue("freeSubscribeConnections") Collection<?> subscriptionFreeConnections) {
      RedissonSingletons.registerMetrics(
          redisClient,
          regularMinIdle,
          regularMax,
          regularSemaphore,
          regularFreeConnections,
          subscriptionMinIdle,
          subscriptionMax,
          subscriptionSemaphore,
          subscriptionFreeConnections);
    }
  }

  @SuppressWarnings("unused")
  public static class SixArgumentConstructorAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(
        @Advice.Argument(0) RedisClient redisClient,
        @Advice.Argument(1) int regularMinIdle,
        @Advice.Argument(2) int regularMax,
        @Advice.Argument(5) MasterSlaveServersConfig config,
        @Advice.FieldValue("freeConnectionsCounter") AsyncSemaphore regularSemaphore,
        @Advice.FieldValue("freeConnections") Collection<?> regularFreeConnections,
        @Advice.FieldValue("freeSubscribeConnectionsCounter") AsyncSemaphore subscriptionSemaphore,
        @Advice.FieldValue("freeSubscribeConnections") Collection<?> subscriptionFreeConnections) {
      RedissonSingletons.registerMetrics(
          redisClient,
          regularMinIdle,
          regularMax,
          regularSemaphore,
          regularFreeConnections,
          config.getSubscriptionConnectionMinimumIdleSize(),
          config.getSubscriptionConnectionPoolSize(),
          subscriptionSemaphore,
          subscriptionFreeConnections);
    }
  }
}
