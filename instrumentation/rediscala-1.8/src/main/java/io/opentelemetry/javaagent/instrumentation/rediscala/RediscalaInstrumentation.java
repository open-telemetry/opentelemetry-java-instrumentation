/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rediscala;

import static io.opentelemetry.javaagent.instrumentation.rediscala.RediscalaClientTracer.tracer;
import static io.opentelemetry.javaagent.tooling.ClassLoaderMatcher.hasClassesNamed;
import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.AgentElementMatchers.safeHasSuperType;
import static io.opentelemetry.javaagent.tooling.matcher.NameMatchers.namedOneOf;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.tooling.Instrumenter;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import redis.RedisCommand;
import scala.concurrent.ExecutionContext;
import scala.concurrent.Future;
import scala.runtime.AbstractFunction1;
import scala.util.Try;

@AutoService(Instrumenter.class)
public final class RediscalaInstrumentation extends Instrumenter.Default {

  public RediscalaInstrumentation() {
    super("rediscala", "redis");
  }

  @Override
  public ElementMatcher<ClassLoader> classLoaderMatcher() {
    return hasClassesNamed("redis.Request");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return safeHasSuperType(
        namedOneOf(
            "redis.ActorRequest",
            "redis.Request",
            "redis.BufferedRequest",
            "redis.RoundRobinPoolRequest"));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      RediscalaInstrumentation.class.getName() + "$OnCompleteHandler",
      packageName + ".RediscalaClientTracer",
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod()
            .and(isPublic())
            .and(named("send"))
            .and(takesArgument(0, named("redis.RedisCommand")))
            .and(returns(named("scala.concurrent.Future"))),
        RediscalaInstrumentation.class.getName() + "$RediscalaAdvice");
  }

  public static class RediscalaAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(0) RedisCommand cmd,
        @Advice.Local("otelSpan") Span span,
        @Advice.Local("otelScope") Scope scope) {
      span = tracer().startSpan(cmd, cmd);
      scope = tracer().startScope(span);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Local("otelSpan") Span span,
        @Advice.Local("otelScope") Scope scope,
        @Advice.Thrown Throwable throwable,
        @Advice.FieldValue("executionContext") ExecutionContext ctx,
        @Advice.Return(readOnly = false) Future<Object> responseFuture) {
      scope.close();

      if (throwable != null) {
        tracer().endExceptionally(span, throwable);
      } else {
        responseFuture.onComplete(new OnCompleteHandler(span), ctx);
      }
    }
  }

  public static class OnCompleteHandler extends AbstractFunction1<Try<Object>, Void> {
    private final Span span;

    public OnCompleteHandler(Span span) {
      this.span = span;
    }

    @Override
    public Void apply(Try<Object> result) {
      if (result.isFailure()) {
        tracer().endExceptionally(span, result.failed().get());
      } else {
        tracer().end(span);
      }
      return null;
    }
  }
}
