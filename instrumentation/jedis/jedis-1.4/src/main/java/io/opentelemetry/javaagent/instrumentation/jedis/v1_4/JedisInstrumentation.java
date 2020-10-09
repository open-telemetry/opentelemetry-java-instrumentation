/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.jedis.v1_4;

import static io.opentelemetry.instrumentation.auto.jedis.v1_4.JedisClientTracer.TRACER;
import static io.opentelemetry.javaagent.tooling.ClassLoaderMatcher.hasClassesNamed;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.instrumentation.api.CallDepthThreadLocalMap;
import io.opentelemetry.javaagent.tooling.Instrumenter;
import io.opentelemetry.trace.Span;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import redis.clients.jedis.Connection;
import redis.clients.jedis.Protocol.Command;

@AutoService(Instrumenter.class)
public final class JedisInstrumentation extends Instrumenter.Default {

  public JedisInstrumentation() {
    super("jedis", "redis");
  }

  @Override
  public ElementMatcher<ClassLoader> classLoaderMatcher() {
    // Avoid matching 3.x
    return not(hasClassesNamed("redis.clients.jedis.commands.ProtocolCommand"));
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("redis.clients.jedis.Connection");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".JedisClientTracer",
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod()
            .and(named("sendCommand"))
            .and(takesArgument(0, named("redis.clients.jedis.Protocol$Command"))),
        JedisInstrumentation.class.getName() + "$JedisAdvice");
    // FIXME: This instrumentation only incorporates sending the command, not processing the result.
  }

  public static class JedisAdvice {

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

      span = TRACER.startSpan(connection, command);
      scope = TRACER.startScope(span);
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
        TRACER.endExceptionally(span, throwable);
      } else {
        TRACER.end(span);
      }
    }
  }
}
