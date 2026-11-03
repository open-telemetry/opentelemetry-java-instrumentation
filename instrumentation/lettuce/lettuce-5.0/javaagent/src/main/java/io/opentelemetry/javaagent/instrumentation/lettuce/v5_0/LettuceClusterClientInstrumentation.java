/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.lettuce.core.RedisChannelHandler;
import io.lettuce.core.RedisURI;
import io.lettuce.core.cluster.RedisClusterClient;
import io.lettuce.core.protocol.DefaultEndpoint;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class LettuceClusterClientInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("io.lettuce.core.cluster.RedisClusterClient");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isConstructor().and(takesArgument(1, Iterable.class)),
        getClass().getName() + "$ConstructorAdvice");
    // Lettuce 5.0 and 6.0+ place DefaultEndpoint and RedisURI at indexes 1 and 2.
    transformer.applyAdviceToMethod(
        nameStartsWith("connectStateful")
            .and(takesArgument(1, named("io.lettuce.core.protocol.DefaultEndpoint")))
            .and(takesArgument(2, named("io.lettuce.core.RedisURI"))),
        getClass().getName() + "$AttachEndpointAdvice");
    // Lettuce 5.1-5.3 insert a codec before DefaultEndpoint.
    transformer.applyAdviceToMethod(
        nameStartsWith("connectStateful")
            .and(takesArgument(2, named("io.lettuce.core.protocol.DefaultEndpoint")))
            .and(takesArgument(3, named("io.lettuce.core.RedisURI"))),
        getClass().getName() + "$AttachEndpointWithCodecAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConstructorAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This RedisClusterClient client,
        @Advice.Argument(1) @Nullable Iterable<RedisURI> initialUris) {
      LettuceServerTargets.capture(client, initialUris);
    }
  }

  @SuppressWarnings("unused")
  public static class AttachEndpointAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static void onEnter(
        @Advice.This RedisClusterClient client,
        @Advice.Argument(0) Object connection,
        @Advice.Argument(1) DefaultEndpoint endpoint,
        @Advice.Argument(2) RedisURI redisUri) {
      AttachEndpointHelper.attach(client, connection, endpoint, redisUri);
    }
  }

  @SuppressWarnings("unused")
  public static class AttachEndpointWithCodecAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static void onEnter(
        @Advice.This RedisClusterClient client,
        @Advice.Argument(0) Object connection,
        @Advice.Argument(2) DefaultEndpoint endpoint,
        @Advice.Argument(3) RedisURI redisUri) {
      AttachEndpointHelper.attach(client, connection, endpoint, redisUri);
    }
  }

  public static class AttachEndpointHelper {

    public static void attach(
        RedisClusterClient client, Object connection, DefaultEndpoint endpoint, RedisURI redisUri) {
      RedisServerTarget target = LettuceServerTargets.get(client);
      LettuceConnectionState.captureEndpoint(endpoint, redisUri.getDatabase(), target);
      if (connection instanceof RedisChannelHandler) {
        RedisChannelHandler<?, ?> connectionHandler = (RedisChannelHandler<?, ?>) connection;
        LettuceServerTargets.copy(client, connectionHandler);
      }
    }

    private AttachEndpointHelper() {}
  }
}
