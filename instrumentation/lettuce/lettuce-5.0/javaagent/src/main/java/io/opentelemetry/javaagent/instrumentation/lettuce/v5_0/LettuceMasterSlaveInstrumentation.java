/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0;

import static io.opentelemetry.javaagent.instrumentation.lettuce.v5_0.LettuceSingletons.MASTER_SLAVE_CONNECTION_DELEGATE;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.lettuce.core.RedisChannelHandler;
import io.lettuce.core.RedisURI;
import io.lettuce.core.masterslave.StatefulRedisMasterSlaveConnection;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class LettuceMasterSlaveInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return namedOneOf(
        "io.lettuce.core.masterslave.MasterSlave",
        "io.lettuce.core.masterslave.MasterSlaveConnectionWrapper");
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
    transformer.applyAdviceToMethod(
        isConstructor()
            .and(
                takesArgument(
                    0,
                    named("io.lettuce.core.masterreplica.StatefulRedisMasterReplicaConnection"))),
        getClass().getName() + "$WrapperConstructorAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConnectAdvice {

    @Advice.AssignReturned.ToArguments(@ToArgument(value = 2, index = 1))
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Object[] onEnter(@Advice.Argument(2) Object targetSource) {
      if (targetSource instanceof RedisURI) {
        return new Object[] {LettuceServerTargets.of((RedisURI) targetSource), targetSource};
      }
      // the iterable may only be traversed once, so the method continues with the snapshot
      List<Object> snapshot = new ArrayList<>();
      for (Object redisUri : (Iterable<?>) targetSource) {
        snapshot.add(redisUri);
      }
      return new Object[] {LettuceServerTargets.ofMasterSlaveUris(snapshot), snapshot};
    }

    @SuppressWarnings("FutureReturnValueIgnored")
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Enter Object[] enter, @Advice.Return @Nullable Object connection) {
      RedisServerTarget target = (RedisServerTarget) enter[0];
      if (target == null) {
        return;
      }
      if (connection instanceof CompletableFuture) {
        ((CompletableFuture<?>) connection)
            .whenComplete(new SetMasterSlaveTargetBiConsumer(target));
      } else {
        SetMasterSlaveTargetBiConsumer.setTarget(connection, target);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class WrapperConstructorAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This StatefulRedisMasterSlaveConnection<?, ?> connection,
        @Advice.Argument(0) Object delegate) {
      if (delegate instanceof RedisChannelHandler) {
        MASTER_SLAVE_CONNECTION_DELEGATE.set(connection, (RedisChannelHandler<?, ?>) delegate);
      }
    }
  }
}
