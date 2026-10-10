/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rediscala.v1_8;

import static io.opentelemetry.javaagent.instrumentation.rediscala.v1_8.RediscalaSingletons.TRANSACTION_STATE;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import redis.commands.TransactionBuilder;

class TransactionBuilderInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return namedOneOf(
        "redis.RedisClient",
        "redis.RedisClientMasterSlaves",
        "redis.SentinelMonitoredRedisClient",
        "redis.SentinelMonitoredRedisClientMasterSlaves");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        namedOneOf("multi", "transaction", "watch"), getClass().getName() + "$CreateAdvice");
  }

  @SuppressWarnings("unused")
  public static class CreateAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This Object client,
        @Advice.Return @Nullable TransactionBuilder transactionBuilder) {
      if (transactionBuilder != null) {
        TRANSACTION_STATE.set(transactionBuilder, new RediscalaTransactionState(client));
      }
    }
  }
}
