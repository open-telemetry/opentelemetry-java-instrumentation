/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redissonmetrics.v2_3;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
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

class ClientConnectionsEntryInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("org.redisson.connection.ClientConnectionsEntry");
  }

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
        getClass().getName() + "$ConstructorAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConstructorAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(
        @Advice.Argument(0) RedisClient redisClient,
        @Advice.Argument(1) int poolMinSize,
        @Advice.Argument(2) int poolMaxSize,
        @Advice.Argument(3) int subscriptionPoolMinSize,
        @Advice.Argument(4) int subscriptionPoolMaxSize,
        @Advice.Argument(5) Object connectionManager,
        @Advice.FieldValue("freeConnectionsCounter") Object freeConnectionsCounter,
        @Advice.FieldValue("freeConnections") Collection<?> freeConnections,
        @Advice.FieldValue("freeSubscribeConnectionsCounter")
            Object freeSubscribeConnectionsCounter,
        @Advice.FieldValue("freeSubscribeConnections") Collection<?> freeSubscribeConnections) {
      RedissonSingletons.registerMetrics(
          redisClient,
          poolMinSize,
          poolMaxSize,
          freeConnectionsCounter,
          freeConnections,
          subscriptionPoolMinSize,
          subscriptionPoolMaxSize,
          connectionManager,
          freeSubscribeConnectionsCounter,
          freeSubscribeConnections);
    }
  }
}
