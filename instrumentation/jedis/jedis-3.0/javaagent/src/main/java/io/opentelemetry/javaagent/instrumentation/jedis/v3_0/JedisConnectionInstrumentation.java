/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v3_0;

import static io.opentelemetry.javaagent.instrumentation.jedis.v3_0.JedisSingletons.instrumenter;
import static java.util.Arrays.asList;
import static net.bytebuddy.matcher.ElementMatchers.is;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.jedis.common.v1_4.JedisRequestContext;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import redis.clients.jedis.Connection;
import redis.clients.jedis.commands.ProtocolCommand;

class JedisConnectionInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("redis.clients.jedis.Connection");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(isConstructor(), getClass().getName() + "$ConstructorAdvice");
    transformer.applyAdviceToMethod(
        namedOneOf("setHost", "setPort").and(takesArguments(1)),
        getClass().getName() + "$UpdateTargetAdvice");

    transformer.applyAdviceToMethod(
        named("sendCommand")
            .and(takesArguments(2))
            .and(takesArgument(0, named("redis.clients.jedis.commands.ProtocolCommand")))
            .and(takesArgument(1, is(byte[][].class))),
        getClass().getName() + "$SendCommandAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConstructorAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.This Connection connection) {
      JedisSingletons.setConnectionTarget(
          connection, RedisServerTarget.ofHostAndPort(connection.getHost(), connection.getPort()));
    }
  }

  @SuppressWarnings("unused")
  public static class UpdateTargetAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.This Connection connection) {
      JedisSingletons.setConnectionTarget(
          connection, RedisServerTarget.ofHostAndPort(connection.getHost(), connection.getPort()));
    }
  }

  @SuppressWarnings("unused")
  public static class SendCommandAdvice {

    public static class AdviceScope {
      private final Context context;
      private final Scope scope;
      private final JedisRequest request;

      private AdviceScope(Context context, Scope scope, JedisRequest request) {
        this.context = context;
        this.scope = scope;
        this.request = request;
      }

      @Nullable
      public static AdviceScope start(
          Connection connection, ProtocolCommand command, byte[][] args) {
        if (JedisPipelineContext.inTransactionFraming()) {
          // MULTI/EXEC/DISCARD frame a batched transaction; they are represented by the MULTI
          // batch span rather than getting their own spans.
          return null;
        }
        Context parentContext = Context.current();
        JedisRequest request = JedisRequest.create(connection, command, asList(args));
        if (JedisPipelineContext.capture(request)) {
          // A pipeline or transaction is active, so this command is captured and aggregated into
          // the batch span created at sync()/exec() rather than getting its own span.
          return null;
        }
        if (!instrumenter().shouldStart(parentContext, request)) {
          return null;
        }
        Context context = instrumenter().start(parentContext, request);
        return new AdviceScope(context, context.makeCurrent(), request);
      }

      public void end(@Nullable Throwable throwable) {
        scope.close();
        JedisRequestContext.endIfNotAttached(instrumenter(), context, request, throwable);
      }
    }

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static AdviceScope onEnter(
        @Advice.This Connection connection,
        @Advice.Argument(0) ProtocolCommand command,
        @Advice.Argument(1) byte[][] args) {
      return AdviceScope.start(connection, command, args);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void stopSpan(
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter @Nullable AdviceScope adviceScope) {
      if (adviceScope != null) {
        adviceScope.end(throwable);
      }
    }
  }
}
