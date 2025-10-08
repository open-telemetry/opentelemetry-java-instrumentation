/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v4_0;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.instrumentation.jedis.v4_0.JedisSingletons.instrumenter;
import static java.util.Arrays.asList;
import static net.bytebuddy.matcher.ElementMatchers.is;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.jedis.JedisRequestContext;
import java.net.Socket;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import redis.clients.jedis.CommandArguments;
import redis.clients.jedis.commands.ProtocolCommand;

public class JedisConnectionInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("redis.clients.jedis.Connection");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("sendCommand"))
            .and(takesArguments(1))
            .and(takesArgument(0, named("redis.clients.jedis.CommandArguments"))),
        this.getClass().getName() + "$SendCommand2Advice");

    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("sendCommand"))
            .and(takesArguments(2))
            .and(takesArgument(0, named("redis.clients.jedis.commands.ProtocolCommand")))
            .and(takesArgument(1, is(byte[][].class))),
        this.getClass().getName() + "$SendCommandAdvice");
  }

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
    public static AdviceScope start(JedisRequest request) {
      Context parentContext = currentContext();
      if (!instrumenter().shouldStart(parentContext, request)) {
        return null;
      }
      Context context = instrumenter().start(parentContext, request);
      return new AdviceScope(context, context.makeCurrent(), request);
    }

    public void end(Socket socket, @Nullable Throwable throwable) {
      request.setSocket(socket);
      scope.close();
      JedisRequestContext.endIfNotAttached(instrumenter(), context, request, throwable);
    }
  }

  @SuppressWarnings("unused")
  public static class SendCommandAdvice {

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AdviceScope onEnter(
        @Advice.Argument(0) ProtocolCommand command, @Advice.Argument(1) byte[][] args) {
      return AdviceScope.start(JedisRequest.create(command, asList(args)));
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
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
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AdviceScope onEnter(@Advice.Argument(0) CommandArguments command) {
      return AdviceScope.start(JedisRequest.create(command));
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
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
