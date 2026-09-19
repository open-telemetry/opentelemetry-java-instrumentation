/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0;

import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.named;
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
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class LettuceMasterSlaveInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    // These entry points delegate to each other in different Lettuce versions. Exit advice updates
    // only the inner RedisChannelHandler, so instrumenting both names is idempotent.
    return named("io.lettuce.core.masterslave.MasterSlave")
        .or(named("io.lettuce.core.masterreplica.MasterReplica"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isPublic()
            .and(isStatic())
            .and(named("connect").or(named("connectAsync")))
            .and(takesArguments(3))
            .and(
                takesArgument(2, named("io.lettuce.core.RedisURI"))
                    .or(takesArgument(2, Iterable.class))),
        getClass().getName() + "$ConnectAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConnectAdvice {

    @Advice.AssignReturned.ToArguments(@ToArgument(value = 2, index = 1))
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Object[] onEnter(@Advice.Argument(2) Object targetSource) {
      if (targetSource instanceof RedisURI) {
        return new Object[] {LettuceServerTargets.of((RedisURI) targetSource), targetSource};
      }
      List<Object> snapshot = new ArrayList<>();
      for (Object redisUri : (Iterable<?>) targetSource) {
        snapshot.add(redisUri);
      }
      return new Object[] {LettuceServerTargets.ofMasterSlaveUris(snapshot), snapshot};
    }

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Enter Object[] enter, @Advice.Return @Nullable Object result) {
      RedisServerTarget target = (RedisServerTarget) enter[0];
      if (result instanceof RedisChannelHandler) {
        setTarget(result, target);
      } else if (result instanceof CompletableFuture) {
        ((CompletableFuture<?>) result).thenAccept(new SetTargetConsumer(target));
      }
    }

    public static void setTarget(Object connection, @Nullable RedisServerTarget target) {
      RedisChannelHandler<?, ?> connectionHandler = (RedisChannelHandler<?, ?>) connection;
      LettuceConnectionState.updateServerTarget(connectionHandler, target);
    }
  }

  public static class SetTargetConsumer implements Consumer<Object> {
    @Nullable private final RedisServerTarget target;

    public SetTargetConsumer(@Nullable RedisServerTarget target) {
      this.target = target;
    }

    @Override
    public void accept(Object connection) {
      if (connection instanceof RedisChannelHandler) {
        ConnectAdvice.setTarget(connection, target);
      }
    }
  }
}
