/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0;

import static io.opentelemetry.javaagent.instrumentation.lettuce.v5_0.LettuceSingletons.CONNECTION_TARGET;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.lettuce.core.RedisChannelHandler;
import io.lettuce.core.RedisURI;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatcher;

public class LettuceMasterSlaveInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("io.lettuce.core.masterslave.MasterSlave");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isPublic()
            .and(isStatic())
            .and(namedOneOf("connect", "connectAsync"))
            .and(takesArguments(3))
            .and(
                takesArgument(2, named("io.lettuce.core.RedisURI"))
                    .or(takesArgument(2, named("java.lang.Iterable")))),
        getClass().getName() + "$ConnectAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConnectAdvice {

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static RedisServerTarget onEnter(
        @Advice.Argument(value = 2, readOnly = false, typing = Assigner.Typing.DYNAMIC)
            Object targetSource) {
      if (targetSource instanceof RedisURI) {
        return LettuceServerTargets.of((RedisURI) targetSource);
      }
      List<Object> snapshot = new ArrayList<>();
      for (Object redisUri : (Iterable<?>) targetSource) {
        snapshot.add(redisUri);
      }
      targetSource = snapshot;
      return LettuceServerTargets.ofMasterSlaveUris(snapshot);
    }

    @SuppressWarnings("FutureReturnValueIgnored")
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Enter @Nullable RedisServerTarget target,
        @Advice.Return @Nullable Object connection) {
      if (target == null) {
        return;
      }
      if (connection instanceof CompletableFuture) {
        ((CompletableFuture<?>) connection)
            .whenComplete(new SetMasterSlaveTargetBiConsumer(target));
      } else if (connection instanceof RedisChannelHandler) {
        CONNECTION_TARGET.set((RedisChannelHandler<?, ?>) connection, target);
      }
    }
  }
}
