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
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import reactor.core.publisher.Mono;

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
    transformer.applyAdviceToMethod(
        named("connectStatefulAsync")
            .and(takesArgument(2, named("io.lettuce.core.protocol.DefaultEndpoint")))
            .and(takesArgument(3, named("io.lettuce.core.RedisURI")))
            .and(takesArgument(4, named("reactor.core.publisher.Mono"))),
        getClass().getName() + "$AttachEndpointWithCodecAdvice");
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
    @Advice.AssignReturned.ToArguments(@ToArgument(3))
    public static Object onEnter(
        @Advice.This RedisClusterClient client,
        @Advice.Argument(1) DefaultEndpoint endpoint,
        @Advice.Argument(2) RedisURI redisUri,
        @Advice.Argument(3) Object socketAddressSource) {
      return AttachEndpointHelper.attach(client, endpoint, redisUri, socketAddressSource);
    }
  }

  @SuppressWarnings("unused")
  public static class AttachEndpointWithCodecAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    @Advice.AssignReturned.ToArguments(@ToArgument(4))
    public static Object onEnter(
        @Advice.This RedisClusterClient client,
        @Advice.Argument(2) DefaultEndpoint endpoint,
        @Advice.Argument(3) RedisURI redisUri,
        @Advice.Argument(4) Object socketAddressSource) {
      return AttachEndpointHelper.attach(client, endpoint, redisUri, socketAddressSource);
    }
  }

  public static class AttachEndpointHelper {

    public static Object attach(
        RedisClusterClient client,
        DefaultEndpoint endpoint,
        RedisURI redisUri,
        Object socketAddressSource) {
      ENDPOINT_DATABASE_INDEX.set(endpoint, redisUri.getDatabase());
      RedisServerTarget clusterTarget = CLUSTER_CLIENT_TARGET.get(client);
      ENDPOINT_TARGET.set(endpoint, clusterTarget);
      if (socketAddressSource instanceof Supplier) {
        Supplier<?> socketAddressSupplier = (Supplier<?>) socketAddressSource;
        return socketAddressSupplier instanceof EndpointAddressSupplier
            ? socketAddressSupplier
            : new EndpointAddressSupplier(socketAddressSupplier, endpoint);
      }
      if (socketAddressSource instanceof Mono) {
        return ((Mono<?>) socketAddressSource).doOnNext(new EndpointAddressConsumer(endpoint));
      }
      return socketAddressSource;
    }

    private AttachEndpointHelper() {}
  }

  public static class EndpointAddressSupplier implements Supplier<SocketAddress> {
    private final Supplier<?> delegate;
    private final DefaultEndpoint endpoint;

    public EndpointAddressSupplier(Supplier<?> delegate, DefaultEndpoint endpoint) {
      this.delegate = delegate;
      this.endpoint = endpoint;
    }

    @Override
    public SocketAddress get() {
      Object address = delegate.get();
      if (address instanceof InetSocketAddress) {
        ENDPOINT_ADDRESS.set(endpoint, (InetSocketAddress) address);
      }
      return (SocketAddress) address;
    }
  }

  public static class EndpointAddressConsumer implements Consumer<Object> {
    private final DefaultEndpoint endpoint;

    public EndpointAddressConsumer(DefaultEndpoint endpoint) {
      this.endpoint = endpoint;
    }

    @Override
    public void accept(Object address) {
      if (address instanceof InetSocketAddress) {
        ENDPOINT_ADDRESS.set(endpoint, (InetSocketAddress) address);
      }
    }
  }
}
