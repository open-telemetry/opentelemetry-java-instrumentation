/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static java.util.logging.Level.FINE;
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
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import reactor.core.publisher.Mono;

class LettuceClusterClientInstrumentation implements TypeInstrumentation {

  private static final Logger logger =
      Logger.getLogger(LettuceClusterClientInstrumentation.class.getName());

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
    @Advice.AssignReturned.ToArguments(@ToArgument(3))
    public static Object onEnter(
        @Advice.This RedisClusterClient client,
        @Advice.Argument(0) Object connection,
        @Advice.Argument(1) DefaultEndpoint endpoint,
        @Advice.Argument(2) RedisURI redisUri,
        @Advice.Argument(3) Object socketAddressSource) {
      return AttachEndpointHelper.attach(
          client, connection, endpoint, redisUri, socketAddressSource);
    }
  }

  @SuppressWarnings("unused")
  public static class AttachEndpointWithCodecAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    @Advice.AssignReturned.ToArguments(@ToArgument(4))
    public static Object onEnter(
        @Advice.This RedisClusterClient client,
        @Advice.Argument(0) Object connection,
        @Advice.Argument(2) DefaultEndpoint endpoint,
        @Advice.Argument(3) RedisURI redisUri,
        @Advice.Argument(4) Object socketAddressSource) {
      return AttachEndpointHelper.attach(
          client, connection, endpoint, redisUri, socketAddressSource);
    }
  }

  public static class AttachEndpointHelper {

    public static Object attach(
        RedisClusterClient client,
        Object connection,
        DefaultEndpoint endpoint,
        RedisURI redisUri,
        Object socketAddressSource) {
      RedisServerTarget target = LettuceServerTargets.get(client);
      LettuceConnectionState.captureEndpoint(endpoint, null, redisUri.getDatabase(), target);
      if (connection instanceof RedisChannelHandler) {
        RedisChannelHandler<?, ?> connectionHandler = (RedisChannelHandler<?, ?>) connection;
        LettuceServerTargets.copy(client, connectionHandler);
      }
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
        captureAddress(endpoint, (InetSocketAddress) address);
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
        captureAddress(endpoint, (InetSocketAddress) address);
      }
    }
  }

  private static void captureAddress(
      DefaultEndpoint endpoint, InetSocketAddress serverAddress) {
    try {
      LettuceConnectionState.updateServerAddress(endpoint, serverAddress);
    } catch (Throwable t) {
      logger.log(FINE, "Failed to attach Lettuce server address", t);
    }
  }
}
