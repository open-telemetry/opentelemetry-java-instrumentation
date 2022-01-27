/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v4_0;

import static io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge.currentContext;
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

  @SuppressWarnings("unused")
  public static class SendCommandAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(0) ProtocolCommand command,
        @Advice.Argument(1) byte[][] args,
        @Advice.Local("otelJedisRequest") JedisRequest request,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      Context parentContext = currentContext();
      request = JedisRequest.create(command, asList(args));
      if (!instrumenter().shouldStart(parentContext, request)) {
        return;
      }

      context = instrumenter().start(parentContext, request);
      scope = context.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.FieldValue("socket") Socket socket,
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelJedisRequest") JedisRequest request,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      if (scope == null) {
        return;
      }

      request.setSocket(socket);

      scope.close();
      JedisRequestContext.end(instrumenter(), context, request, throwable);
    }
  }

  @SuppressWarnings("unused")
  public static class SendCommand2Advice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(0) CommandArguments command,
        @Advice.Local("otelJedisRequest") JedisRequest request,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      Context parentContext = currentContext();
      request = JedisRequest.create(command);
      if (!instrumenter().shouldStart(parentContext, request)) {
        return;
      }

      context = instrumenter().start(parentContext, request);
      scope = context.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.FieldValue("socket") Socket socket,
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelJedisRequest") JedisRequest request,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      if (scope == null) {
        return;
      }

      request.setSocket(socket);

      scope.close();
      JedisRequestContext.end(instrumenter(), context, request, throwable);
    }
  }
}
