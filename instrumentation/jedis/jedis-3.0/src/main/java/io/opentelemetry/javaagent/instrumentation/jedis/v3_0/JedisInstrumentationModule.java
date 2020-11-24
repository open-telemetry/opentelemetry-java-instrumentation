/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v3_0;

import static io.opentelemetry.javaagent.instrumentation.jedis.v3_0.JedisClientTracer.tracer;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.is;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.instrumentation.jedis.v3_0.JedisClientTracer.CommandWithArgs;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.List;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import redis.clients.jedis.Connection;
import redis.clients.jedis.commands.ProtocolCommand;

@AutoService(InstrumentationModule.class)
public final class JedisInstrumentationModule extends InstrumentationModule {

  public JedisInstrumentationModule() {
    super("jedis", "jedis-3.0");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new ConnectionInstrumentation());
  }

  private static final class ConnectionInstrumentation implements TypeInstrumentation {
    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
      return named("redis.clients.jedis.Connection");
    }

    @Override
    public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
      return singletonMap(
          isMethod()
              .and(named("sendCommand"))
              .and(takesArguments(2))
              .and(takesArgument(0, named("redis.clients.jedis.commands.ProtocolCommand")))
              .and(takesArgument(1, is(byte[][].class))),
          JedisInstrumentationModule.class.getName() + "$JedisAdvice");
      // FIXME: This instrumentation only incorporates sending the command, not processing the
      // result.
    }
  }

  public static class JedisAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.This Connection connection,
        @Advice.Argument(0) ProtocolCommand command,
        @Advice.Argument(1) byte[][] args,
        @Advice.Local("otelSpan") Span span,
        @Advice.Local("otelScope") Scope scope) {
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

      if (throwable != null) {
        tracer().endExceptionally(span, throwable);
      } else {
        tracer().end(span);
      }
    }
  }
}
