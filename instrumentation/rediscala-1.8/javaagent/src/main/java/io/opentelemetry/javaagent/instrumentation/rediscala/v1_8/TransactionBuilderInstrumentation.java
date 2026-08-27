/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rediscala.v1_8;

import static io.opentelemetry.javaagent.instrumentation.rediscala.v1_8.RediscalaSingletons.TRANSACTION_CLIENT;
import static io.opentelemetry.javaagent.instrumentation.rediscala.v1_8.RediscalaSingletons.TRANSACTION_ENDPOINT;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.returns;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import redis.RedisClientActorLike;
import redis.commands.TransactionBuilder;

class TransactionBuilderInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return namedOneOf(
        "redis.RedisClient",
        "redis.SentinelMonitoredRedisClient",
        "redis.SentinelMonitoredRedisClientMasterSlaves");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        namedOneOf("multi", "transaction", "watch")
            .and(returns(named("redis.commands.TransactionBuilder"))),
        getClass().getName() + "$CreateAdvice");
  }

  @SuppressWarnings("unused")
  public static class CreateAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This Object client, @Advice.Return TransactionBuilder transactionBuilder) {
      if (transactionBuilder != null) {
        if (client instanceof RedisClientActorLike) {
          TRANSACTION_ENDPOINT.set(
              transactionBuilder, ServerEndpoint.create((RedisClientActorLike) client));
        }
        TRANSACTION_CLIENT.set(transactionBuilder, client);
      }
    }
  }
}
