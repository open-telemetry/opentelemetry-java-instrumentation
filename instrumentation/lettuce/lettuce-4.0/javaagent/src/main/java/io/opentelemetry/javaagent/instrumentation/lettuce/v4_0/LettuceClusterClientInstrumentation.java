/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v4_0;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.lambdaworks.redis.RedisChannelHandler;
import com.lambdaworks.redis.RedisURI;
import com.lambdaworks.redis.cluster.RedisClusterClient;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class LettuceClusterClientInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.lambdaworks.redis.cluster.RedisClusterClient");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    // Every constructor stores the seed URIs, but the constructor argument lists differ.
    transformer.applyAdviceToMethod(isConstructor(), getClass().getName() + "$ConstructorAdvice");
    transformer.applyAdviceToMethod(
        named("connectClusterImpl")
            .and(
                returns(named("com.lambdaworks.redis.cluster.StatefulRedisClusterConnectionImpl"))),
        getClass().getName() + "$AttachConnectionAdvice");
    // Lettuce 4.0-4.3 opens node connections through connectToNode.
    transformer.applyAdviceToMethod(
        named("connectToNode")
            .and(takesArgument(0, named("com.lambdaworks.redis.codec.RedisCodec")))
            .and(returns(named("com.lambdaworks.redis.api.StatefulRedisConnection"))),
        getClass().getName() + "$AttachConnectionAdvice");
    // Lettuce 4.2+ uses connectStateful for every connection, including node connections in 4.4+.
    transformer.applyAdviceToMethod(
        nameStartsWith("connectStateful")
            .and(takesArgument(2, named("com.lambdaworks.redis.RedisURI"))),
        getClass().getName() + "$AttachStatefulConnectionAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConstructorAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This RedisClusterClient client,
        @Advice.FieldValue("initialUris") @Nullable Iterable<RedisURI> initialUris) {
      // RedisURI is mutable, so render the seed list before the client is published.
      LettuceServerTargets.capture(client, initialUris);
    }
  }

  @SuppressWarnings("unused")
  public static class AttachConnectionAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This RedisClusterClient client, @Advice.Return @Nullable Object connection) {
      if (connection instanceof RedisChannelHandler) {
        LettuceServerTargets.copy(client, (RedisChannelHandler<?, ?>) connection);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class AttachStatefulConnectionAdvice {

    // Runs before Lettuce dispatches connection initialization commands.
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static void onEnter(
        @Advice.This RedisClusterClient client,
        @Advice.Argument(1) RedisChannelHandler<?, ?> connection) {
      LettuceServerTargets.copy(client, connection);
    }
  }
}
