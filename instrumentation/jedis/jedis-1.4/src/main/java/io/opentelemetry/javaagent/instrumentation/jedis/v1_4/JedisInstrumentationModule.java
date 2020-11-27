/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v1_4;

import static io.opentelemetry.javaagent.instrumentation.jedis.v1_4.JedisClientTracer.tracer;
import static io.opentelemetry.javaagent.tooling.ClassLoaderMatcher.hasClassesNamed;
import static java.util.Collections.singletonList;
import static net.bytebuddy.matcher.ElementMatchers.is;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.instrumentation.api.CallDepthThreadLocalMap;
import io.opentelemetry.javaagent.instrumentation.jedis.v1_4.JedisClientTracer.CommandWithArgs;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import redis.clients.jedis.Connection;
import redis.clients.jedis.Protocol.Command;

@AutoService(InstrumentationModule.class)
public class JedisInstrumentationModule extends InstrumentationModule {

  public JedisInstrumentationModule() {
    super("jedis", "jedis-1.4");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // Avoid matching 3.x
    return not(hasClassesNamed("redis.clients.jedis.commands.ProtocolCommand"));
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new ConnectionInstrumentation());
  }

  public static class ConnectionInstrumentation implements TypeInstrumentation {
    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
      return named("redis.clients.jedis.Connection");
    }

    @Override
    public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
      // FIXME: This instrumentation only incorporates sending the command, not processing the
      // result.
      Map<ElementMatcher.Junction<MethodDescription>, String> transformers = new HashMap<>();
      transformers.put(
          isMethod()
              .and(named("sendCommand"))
              .and(takesArguments(1))
              .and(takesArgument(0, named("redis.clients.jedis.Protocol$Command"))),
          JedisInstrumentationModule.class.getName() + "$JedisNoArgsAdvice");
      transformers.put(
          isMethod()
              .and(named("sendCommand"))
              .and(takesArguments(2))
              .and(takesArgument(0, named("redis.clients.jedis.Protocol$Command")))
              .and(takesArgument(1, is(byte[][].class))),
          JedisInstrumentationModule.class.getName() + "$JedisArgsAdvice");
      return transformers;
    }
  }

  public static class JedisNoArgsAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.This Connection connection,
        @Advice.Argument(0) Command command,
        @Advice.Local("otelSpan") Span span,
        @Advice.Local("otelScope") Scope scope) {
      int callDepth = CallDepthThreadLocalMap.incrementCallDepth(Connection.class);
      if (callDepth > 0) {
        return;
      }

      span = tracer().startSpan(connection, new CommandWithArgs(command));
      scope = tracer().startScope(span);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelSpan") Span span,
        @Advice.Local("otelScope") Scope scope) {
      if (scope == null) {
        return;
      }
      scope.close();
      CallDepthThreadLocalMap.reset(Connection.class);

      if (throwable != null) {
        tracer().endExceptionally(span, throwable);
      } else {
        tracer().end(span);
      }
    }
  }

  public static class JedisArgsAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.This Connection connection,
        @Advice.Argument(0) Command command,
        @Advice.Argument(1) byte[][] args,
        @Advice.Local("otelSpan") Span span,
        @Advice.Local("otelScope") Scope scope) {
      int callDepth = CallDepthThreadLocalMap.incrementCallDepth(Connection.class);
      if (callDepth > 0) {
        return;
      }

      span = tracer().startSpan(connection, new CommandWithArgs(command, args));
      scope = tracer().startScope(span);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelSpan") Span span,
        @Advice.Local("otelScope") Scope scope) {
      if (scope == null) {
        return;
      }
      scope.close();
      CallDepthThreadLocalMap.reset(Connection.class);

      if (throwable != null) {
        tracer().endExceptionally(span, throwable);
      } else {
        tracer().end(span);
      }
    }
  }
}
