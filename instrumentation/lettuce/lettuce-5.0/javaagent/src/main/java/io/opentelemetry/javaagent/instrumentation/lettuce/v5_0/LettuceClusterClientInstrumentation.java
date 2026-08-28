/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0;

import static io.opentelemetry.javaagent.instrumentation.lettuce.v5_0.LettuceSingletons.CLUSTER_CLIENT_TARGET;
import static io.opentelemetry.javaagent.instrumentation.lettuce.v5_0.LettuceSingletons.CONNECTION_TARGET;
import static io.opentelemetry.javaagent.instrumentation.lettuce.v5_0.LettuceSingletons.ENDPOINT_ADDRESS;
import static io.opentelemetry.javaagent.instrumentation.lettuce.v5_0.LettuceSingletons.ENDPOINT_DATABASE_INDEX;
import static io.opentelemetry.javaagent.instrumentation.lettuce.v5_0.LettuceSingletons.ENDPOINT_TARGET;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.lettuce.core.RedisChannelHandler;
import io.lettuce.core.RedisURI;
import io.lettuce.core.cluster.RedisClusterClient;
import io.lettuce.core.protocol.DefaultEndpoint;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.function.Supplier;
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
        named("connectClusterImpl")
            .and(returns(named("io.lettuce.core.cluster.StatefulRedisClusterConnectionImpl"))),
        getClass().getName() + "$AttachConnectionAdvice");
    transformer.applyAdviceToMethod(
        named("connectClusterPubSubImpl")
            .and(
                returns(
                    named("io.lettuce.core.cluster.pubsub.StatefulRedisClusterPubSubConnection"))),
        getClass().getName() + "$AttachConnectionAdvice");
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
  public static class AttachConnectionAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This RedisClusterClient client, @Advice.Return @Nullable Object connection) {
      RedisServerTarget target = CLUSTER_CLIENT_TARGET.get(client);
      if (target != null && connection instanceof RedisChannelHandler) {
        CONNECTION_TARGET.set((RedisChannelHandler<?, ?>) connection, target);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class AttachEndpointAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static void onEnter(
        @Advice.This RedisClusterClient client,
        @Advice.Argument(1) DefaultEndpoint endpoint,
        @Advice.Argument(2) RedisURI redisUri,
        @Advice.Argument(value = 3, readOnly = false)
            Supplier<SocketAddress> socketAddressSupplier) {
      ENDPOINT_DATABASE_INDEX.set(endpoint, redisUri.getDatabase());
      RedisServerTarget clusterTarget = CLUSTER_CLIENT_TARGET.get(client);
      ENDPOINT_TARGET.set(
          endpoint, clusterTarget != null ? clusterTarget : LettuceServerTargets.of(redisUri));
      if (!(socketAddressSupplier instanceof EndpointAddressSupplier)) {
        socketAddressSupplier = new EndpointAddressSupplier(socketAddressSupplier, endpoint);
      }
    }
  }

  public static final class EndpointAddressSupplier implements Supplier<SocketAddress> {
    private final Supplier<SocketAddress> delegate;
    private final DefaultEndpoint endpoint;

    public EndpointAddressSupplier(Supplier<SocketAddress> delegate, DefaultEndpoint endpoint) {
      this.delegate = delegate;
      this.endpoint = endpoint;
    }

    @Override
    public SocketAddress get() {
      SocketAddress address = delegate.get();
      if (address instanceof InetSocketAddress) {
        ENDPOINT_ADDRESS.set(endpoint, (InetSocketAddress) address);
      }
      return address;
    }
  }
}
