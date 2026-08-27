/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0;

import static io.opentelemetry.javaagent.instrumentation.lettuce.v5_0.LettuceSingletons.CLUSTER_CLIENT_TARGET;
import static io.opentelemetry.javaagent.instrumentation.lettuce.v5_0.LettuceSingletons.ENDPOINT_ADDRESS;
import static io.opentelemetry.javaagent.instrumentation.lettuce.v5_0.LettuceSingletons.ENDPOINT_DATABASE_INDEX;
import static io.opentelemetry.javaagent.instrumentation.lettuce.v5_0.LettuceSingletons.ENDPOINT_TARGET;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.lettuce.core.RedisURI;
import io.lettuce.core.cluster.RedisClusterClient;
import io.lettuce.core.protocol.DefaultEndpoint;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.net.InetSocketAddress;
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
        isConstructor().and(takesArgument(1, named("java.lang.Iterable"))),
        getClass().getName() + "$ConstructorAdvice");
    transformer.applyAdviceToMethod(
        nameStartsWith("connectStateful")
            .and(takesArgument(1, named("io.lettuce.core.protocol.DefaultEndpoint")))
            .and(takesArgument(2, named("io.lettuce.core.RedisURI"))),
        getClass().getName() + "$AttachEndpointAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConstructorAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This RedisClusterClient client,
        @Advice.Argument(1) @Nullable Iterable<RedisURI> initialUris) {
      // a RedisURI is mutable, so the seed list is rendered here and kept immutable
      CLUSTER_CLIENT_TARGET.set(client, LettuceServerTargets.ofUris(initialUris));
    }
  }

  @SuppressWarnings("unused")
  public static class AttachEndpointAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static void onEnter(
        @Advice.This RedisClusterClient client,
        @Advice.Argument(1) DefaultEndpoint endpoint,
        @Advice.Argument(2) RedisURI redisUri) {
      ENDPOINT_DATABASE_INDEX.set(endpoint, redisUri.getDatabase());
      RedisServerTarget clusterTarget = CLUSTER_CLIENT_TARGET.get(client);
      ENDPOINT_TARGET.set(
          endpoint, clusterTarget != null ? clusterTarget : LettuceServerTargets.of(redisUri));
      String host = redisUri.getHost();
      if (host != null) {
        ENDPOINT_ADDRESS.set(
            endpoint, InetSocketAddress.createUnresolved(host, redisUri.getPort()));
      }
    }
  }
}
