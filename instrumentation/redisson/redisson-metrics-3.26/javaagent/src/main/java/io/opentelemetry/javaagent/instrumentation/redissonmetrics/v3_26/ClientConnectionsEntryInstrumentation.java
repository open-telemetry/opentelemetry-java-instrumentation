/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redissonmetrics.v3_26;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.redisson.client.RedisClient;
import org.redisson.config.MasterSlaveServersConfig;
import org.redisson.connection.ClientConnectionsEntry;

class ClientConnectionsEntryInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.redisson.connection.ClientConnectionsEntry");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isConstructor()
            .and(takesArguments(6))
            .and(takesArgument(0, named("org.redisson.client.RedisClient")))
            .and(takesArgument(1, int.class))
            .and(takesArgument(2, int.class))
            .and(takesArgument(3, named("org.redisson.connection.ConnectionManager")))
            .and(takesArgument(4, named("org.redisson.api.NodeType")))
            .and(takesArgument(5, named("org.redisson.config.MasterSlaveServersConfig"))),
        getClass().getName() + "$ConstructorAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConstructorAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(
        @Advice.This ClientConnectionsEntry entry,
        @Advice.Argument(0) RedisClient redisClient,
        @Advice.Argument(1) int regularMinIdle,
        @Advice.Argument(2) int regularMax,
        @Advice.Argument(5) MasterSlaveServersConfig config) {
      RedissonConnectionPoolMetrics.registerMetrics(
          entry, redisClient, regularMinIdle, regularMax, config);
    }
  }
}
