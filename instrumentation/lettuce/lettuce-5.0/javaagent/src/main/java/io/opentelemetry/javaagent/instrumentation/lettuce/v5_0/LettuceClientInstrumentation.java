/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.instrumentation.lettuce.v5_0.LettuceSingletons.CONNECTION_ADDRESS;
import static io.opentelemetry.javaagent.instrumentation.lettuce.v5_0.LettuceSingletons.CONNECTION_DATABASE_INDEX;
import static io.opentelemetry.javaagent.instrumentation.lettuce.v5_0.LettuceSingletons.ENDPOINT_ADDRESS;
import static io.opentelemetry.javaagent.instrumentation.lettuce.v5_0.LettuceSingletons.ENDPOINT_DATABASE_INDEX;
import static io.opentelemetry.javaagent.instrumentation.lettuce.v5_0.LettuceSingletons.connectInstrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isPrivate;
import static net.bytebuddy.matcher.ElementMatchers.nameEndsWith;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.lettuce.core.ConnectionFuture;
import io.lettuce.core.RedisChannelHandler;
import io.lettuce.core.RedisURI;
import io.lettuce.core.protocol.DefaultEndpoint;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.net.InetSocketAddress;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class LettuceClientInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("io.lettuce.core.RedisClient");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isPrivate()
            .and(returns(named("io.lettuce.core.ConnectionFuture")))
            .and(nameStartsWith("connect"))
            .and(nameEndsWith("Async"))
            .and(takesArgument(1, named("io.lettuce.core.RedisURI"))),
        getClass().getName() + "$ConnectAdvice");
    // Every Lettuce 5.0.x release in this module's Muzzle range has exactly one private
    // connectStatefulAsync method returning ConnectionFuture. Under v3-preview this module also
    // handles Lettuce 5.1+, where that method remains unique through the latest tested version, but
    // its parameter layout changes: Lettuce 5.1-5.3 adds a codec before the Endpoint, Lettuce 6
    // removes that codec, and Lettuce 7 adds a trailing argument.
    //
    // Match the stable method name, visibility, and return type instead of argument positions.
    // AttachEndpointAdvice finds RedisChannelHandler, DefaultEndpoint, and RedisURI at runtime. It
    // returns without changes unless both DefaultEndpoint and RedisURI are present. This prevents
    // an unrelated future overload from changing endpoint metadata when its arguments do not have
    // the expected structure.
    transformer.applyAdviceToMethod(
        isPrivate()
            .and(named("connectStatefulAsync"))
            .and(returns(named("io.lettuce.core.ConnectionFuture"))),
        getClass().getName() + "$AttachEndpointAdvice");
  }

  @SuppressWarnings("unused")
  public static class AttachEndpointAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static void onEnter(@Advice.AllArguments Object[] arguments) {
      DefaultEndpoint endpoint = null;
      RedisChannelHandler<?, ?> connection = null;
      RedisURI redisUri = null;

      for (Object argument : arguments) {
        if (argument instanceof DefaultEndpoint) {
          endpoint = (DefaultEndpoint) argument;
        } else if (argument instanceof RedisChannelHandler) {
          connection = (RedisChannelHandler<?, ?>) argument;
        } else if (argument instanceof RedisURI) {
          redisUri = (RedisURI) argument;
        }
      }

      if (endpoint == null || redisUri == null) {
        return;
      }

      int databaseIndex = redisUri.getDatabase();
      ENDPOINT_DATABASE_INDEX.set(endpoint, databaseIndex);
      if (connection != null) {
        CONNECTION_DATABASE_INDEX.set(connection, databaseIndex);
      }

      String host = redisUri.getHost();
      if (host != null) {
        InetSocketAddress address = InetSocketAddress.createUnresolved(host, redisUri.getPort());
        ENDPOINT_ADDRESS.set(endpoint, address);
        if (connection != null) {
          CONNECTION_ADDRESS.set(connection, address);
        }
      }
    }
  }

  @SuppressWarnings("unused")
  public static class ConnectAdvice {

    public static class AdviceScope {
      @Nullable private final Context connectContext;
      private final Scope scope;

      public AdviceScope(@Nullable Context connectContext, Scope scope) {
        this.connectContext = connectContext;
        this.scope = scope;
      }

      public void end(
          @Nullable Throwable throwable,
          RedisURI redisUri,
          @Nullable ConnectionFuture<?> connectionFuture) {

        scope.close();

        if (connectContext == null) {
          return;
        }
        if (throwable != null || connectionFuture == null) {
          connectInstrumenter().end(connectContext, redisUri, null, throwable);
          return;
        }
        connectionFuture.handleAsync(new EndConnectAsyncBiFunction<>(connectContext, redisUri));
      }
    }

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    @Nullable
    public static AdviceScope onEnter(@Advice.Argument(1) RedisURI redisUri) {
      Context parentContext = currentContext();
      if (connectInstrumenter().shouldStart(parentContext, redisUri)) {
        Context connectContext = connectInstrumenter().start(parentContext, redisUri);
        Scope scope = connectContext.makeCurrent();
        return new AdviceScope(connectContext, scope);
      }

      return new AdviceScope(null, parentContext.makeCurrent());
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void stopSpan(
        @Advice.Argument(1) RedisURI redisUri,
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Return @Nullable ConnectionFuture<?> connectionFuture,
        @Advice.Enter @Nullable AdviceScope adviceScope) {
      if (adviceScope != null) {
        adviceScope.end(throwable, redisUri, connectionFuture);
      }
    }
  }
}
