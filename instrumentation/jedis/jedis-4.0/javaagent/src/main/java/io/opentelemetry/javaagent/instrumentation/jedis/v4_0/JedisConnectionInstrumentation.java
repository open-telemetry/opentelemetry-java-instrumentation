/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v4_0;

import static io.opentelemetry.javaagent.instrumentation.jedis.v4_0.JedisSingletons.instrumenter;
import static io.opentelemetry.javaagent.instrumentation.jedis.v4_0.JedisSingletons.setConnectionInfo;
import static java.util.Arrays.asList;
import static net.bytebuddy.matcher.ElementMatchers.is;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.jedis.common.JedisBatchState;
import io.opentelemetry.javaagent.instrumentation.jedis.common.JedisRedisErrorType;
import io.opentelemetry.javaagent.instrumentation.jedis.common.JedisRequestContext;
import java.net.Socket;
import java.util.List;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import redis.clients.jedis.CommandArguments;
import redis.clients.jedis.Connection;
import redis.clients.jedis.JedisSocketFactory;
import redis.clients.jedis.commands.ProtocolCommand;

class JedisConnectionInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("redis.clients.jedis.Connection");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        namedOneOf("getMany", "getObjectMultiBulkReply"),
        getClass().getName() + "$BatchResponseAdvice");
    transformer.applyAdviceToMethod(isConstructor(), getClass().getName() + "$ConstructorAdvice");

    transformer.applyAdviceToMethod(
        named("sendCommand")
            .and(takesArguments(1))
            .and(takesArgument(0, named("redis.clients.jedis.CommandArguments"))),
        getClass().getName() + "$SendCommand2Advice");

    transformer.applyAdviceToMethod(
        named("sendCommand")
            .and(takesArguments(2))
            .and(takesArgument(0, named("redis.clients.jedis.commands.ProtocolCommand")))
            .and(takesArgument(1, is(byte[][].class))),
        getClass().getName() + "$SendCommandAdvice");
  }

  @SuppressWarnings("unused")
  public static class BatchResponseAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This Connection connection, @Advice.Return @Nullable List<?> replies) {
      JedisBatchState.capture(connection, replies);
    }
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
      if (JedisPipelineContext.capture(request)) {
        // A nested pipeline or transaction can run while an outer transaction builds responses.
        // Capture its commands before suppressing actual transaction framing commands.
        return new AdviceScope(null, null, request);
      }
      if (JedisPipelineContext.inTransactionFraming()) {
        // MULTI/EXEC/DISCARD frame a batched transaction; they are represented by the MULTI batch
        // span rather than getting their own spans.
        return null;
      }
      Context parentContext = Context.current();
      if (!instrumenter().shouldStart(parentContext, request)) {
        return null;
      }
      Context context = instrumenter().start(parentContext, request);
      return new AdviceScope(context, context.makeCurrent(), request);
    }

    public void end(@Nullable Socket socket, @Nullable Throwable throwable) {
      // sendCommand may connect after start(), so capture the socket after the command is sent.
      request.setSocket(socket);
      if (scope == null || context == null) {
        return;
      }
      scope.close();
      JedisRequestContext.endIfNotAttached(
          instrumenter(), context, request, JedisRedisErrorType.getErrorType(throwable), throwable);
    }
  }

  @SuppressWarnings("unused")
  public static class ConstructorAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This Connection connection,
        @Advice.FieldValue("socketFactory") JedisSocketFactory socketFactory,
        @Advice.Argument(value = 1, optional = true) @Nullable Object clientConfig) {
      setConnectionInfo(connection, socketFactory, clientConfig);
    }
  }

  @SuppressWarnings("unused")
  public static class SendCommandAdvice {

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static AdviceScope onEnter(
        @Advice.This Connection connection,
        @Advice.Argument(0) ProtocolCommand command,
        @Advice.Argument(1) byte[][] args) {
      return AdviceScope.start(JedisRequest.create(connection, command, asList(args)));
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void stopSpan(
        @Advice.FieldValue("socket") Socket socket,
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter @Nullable AdviceScope adviceScope) {
      if (adviceScope != null) {
        adviceScope.end(socket, throwable);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class SendCommand2Advice {

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static AdviceScope onEnter(
        @Advice.This Connection connection, @Advice.Argument(0) CommandArguments command) {
      return AdviceScope.start(JedisRequest.create(connection, command));
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void stopSpan(
        @Advice.FieldValue("socket") Socket socket,
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter @Nullable AdviceScope adviceScope) {
      if (adviceScope != null) {
        adviceScope.end(socket, throwable);
      }
    }
  }
}
