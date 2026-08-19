/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v4_0;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.instrumentation.lettuce.v4_0.LettuceSingletons.connectInstrumenter;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.lambdaworks.redis.RedisChannelHandler;
import com.lambdaworks.redis.RedisURI;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class LettuceConnectInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.lambdaworks.redis.RedisClient");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("connectStandalone"), getClass().getName() + "$ConnectAdvice");
    // connectStateful in lettuce 4.0-4.3, connectStatefulAsync in lettuce 4.4+
    transformer.applyAdviceToMethod(
        nameStartsWith("connectStateful")
            .and(takesArgument(1, named("com.lambdaworks.redis.StatefulRedisConnectionImpl")))
            .and(takesArgument(2, named("com.lambdaworks.redis.RedisURI"))),
        getClass().getName() + "$AttachConnectionAdvice");
  }

  @SuppressWarnings("unused")
  public static class AttachConnectionAdvice {

    // runs before lettuce dispatches the connection initialization commands, so that a SELECT for a
    // non-default database can find the uri too
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static void onEnter(
        @Advice.Argument(1) RedisChannelHandler<?, ?> connection,
        @Advice.Argument(2) RedisURI redisUri) {
      if (redisUri.getHost() != null) {
        LettuceSingletons.attachConnection(connection, redisUri);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class ConnectAdvice {

    public static class AdviceScope {
      private final Context context;
      private final Scope scope;

      public AdviceScope(Context context, Scope scope) {
        this.context = context;
        this.scope = scope;
      }

      public void end(@Nullable Throwable throwable, RedisURI redisUri) {
        scope.close();
        connectInstrumenter().end(context, redisUri, null, throwable);
      }
    }

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    @Nullable
    public static AdviceScope onEnter(@Advice.Argument(1) RedisURI redisUri) {
      Context parentContext = currentContext();
      if (!connectInstrumenter().shouldStart(parentContext, redisUri)) {
        return null;
      }

      Context context = connectInstrumenter().start(parentContext, redisUri);
      return new AdviceScope(context, context.makeCurrent());
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Argument(1) RedisURI redisUri,
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter @Nullable AdviceScope adviceScope) {
      if (adviceScope != null) {
        adviceScope.end(throwable, redisUri);
      }
    }
  }
}
