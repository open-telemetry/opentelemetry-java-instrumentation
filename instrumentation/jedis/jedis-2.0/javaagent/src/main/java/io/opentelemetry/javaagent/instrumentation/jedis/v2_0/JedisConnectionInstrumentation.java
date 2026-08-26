/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v2_0;

import static io.opentelemetry.javaagent.instrumentation.jedis.v2_0.JedisSingletons.instrumenter;
import static java.util.Arrays.asList;
import static net.bytebuddy.matcher.ElementMatchers.is;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.jedis.common.v1_4.JedisRequestContext;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatcher;
import redis.clients.jedis.Connection;
import redis.clients.jedis.Protocol;

class JedisConnectionInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("redis.clients.jedis.Connection");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("sendCommand")
            .and(takesArguments(1))
            .and(
                takesArgument(
                    0,
                    namedOneOf(
                        "redis.clients.jedis.Protocol$Command",
                        "redis.clients.jedis.ProtocolCommand"))),
        getClass().getName() + "$SendCommandNoArgsAdvice");
    transformer.applyAdviceToMethod(
        named("sendCommand")
            .and(takesArguments(2))
            .and(
                takesArgument(
                    0,
                    namedOneOf(
                        "redis.clients.jedis.Protocol$Command",
                        "redis.clients.jedis.ProtocolCommand")))
            .and(takesArgument(1, is(byte[][].class))),
        getClass().getName() + "$SendCommandWithArgsAdvice");
  }

  public static class AdviceScope {
    @Nullable private final Context context;
    @Nullable private final Scope scope;
    private final JedisRequest request;

    private AdviceScope(@Nullable Context context, @Nullable Scope scope, JedisRequest request) {
      this.context = context;
      this.scope = scope;
      this.request = request;
    }

    @Nullable
    public static AdviceScope start(JedisRequest request) {
      if (JedisPipelineContext.inTransactionFraming()) {
        // MULTI/EXEC/DISCARD frame a batched transaction; they are represented by the MULTI batch
        // span rather than getting their own spans.
        return null;
      }
      Context parentContext = Context.current();
      if (JedisPipelineContext.capture(request)) {
        // Keep the request until method exit so its post-send peer snapshot is available to the
        // batch span created at sync()/exec().
        return new AdviceScope(null, null, request);
      }
      if (!instrumenter().shouldStart(parentContext, request)) {
        return null;
      }
      Context context = instrumenter().start(parentContext, request);
      return new AdviceScope(context, context.makeCurrent(), request);
    }

    public void end(@Nullable Throwable throwable) {
      try {
        if (throwable == null) {
          request.capturePeerAddress();
        }
      } finally {
        Context context = this.context;
        if (scope != null && context != null) {
          scope.close();
          JedisRequestContext.endIfNotAttached(instrumenter(), context, request, throwable);
        }
      }
    }
  }

  @SuppressWarnings("unused")
  public static class SendCommandNoArgsAdvice {

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static AdviceScope onEnter(
        @Advice.This Connection connection,
        @Advice.Argument(value = 0, typing = Assigner.Typing.DYNAMIC) Protocol.Command command) {
      JedisRequest request = JedisRequest.create(connection, command);
      return AdviceScope.start(request);
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

  @SuppressWarnings("unused")
  public static class SendCommandWithArgsAdvice {

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static AdviceScope onEnter(
        @Advice.This Connection connection,
        @Advice.Argument(value = 0, typing = Assigner.Typing.DYNAMIC) Protocol.Command command,
        @Advice.Argument(1) byte[][] args) {
      JedisRequest request = JedisRequest.create(connection, command, asList(args));
      return AdviceScope.start(request);
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
