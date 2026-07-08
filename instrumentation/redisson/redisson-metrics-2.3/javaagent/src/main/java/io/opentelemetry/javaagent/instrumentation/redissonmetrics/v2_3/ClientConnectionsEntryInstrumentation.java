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
import java.util.function.IntSupplier;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.redisson.api.NodeType;
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
        @Advice.Argument(6) NodeType nodeType,
        @Advice.FieldValue("freeConnectionsCounter") Object freeConnectionsCounter) {
      IntSupplier availableConnections =
          AsyncSemaphoreAccessor.availableConnectionsSupplier(freeConnectionsCounter);
      if (availableConnections == null) {
        return;
      }

      RedissonConnectionPoolMetrics.registerMetrics(
          redisClient,
          poolMinSize,
          poolMaxSize,
          nodeType,
          availableConnections,
          AsyncSemaphoreAccessor.pendingRequestsSupplier(freeConnectionsCounter));
    }
  }
}
