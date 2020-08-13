/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.auto.instrumentation.rediscala;

import static io.opentelemetry.auto.instrumentation.rediscala.RediscalaClientDecorator.DECORATE;
import static io.opentelemetry.auto.instrumentation.rediscala.RediscalaClientDecorator.TRACER;
import static io.opentelemetry.auto.tooling.ClassLoaderMatcher.hasClassesNamed;
import static io.opentelemetry.auto.tooling.bytebuddy.matcher.AgentElementMatchers.safeHasSuperType;
import static io.opentelemetry.auto.tooling.matcher.NameMatchers.namedOneOf;
import static io.opentelemetry.trace.Span.Kind.CLIENT;
import static io.opentelemetry.trace.TracingContextUtils.currentContextWith;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import io.opentelemetry.auto.tooling.Instrumenter;
import io.opentelemetry.instrumentation.auto.api.SpanWithScope;
import io.opentelemetry.trace.Span;
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
      packageName + ".RediscalaClientDecorator",
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
    public static SpanWithScope onEnter(@Advice.Argument(0) final RedisCommand cmd) {
      String statement = DECORATE.spanNameForClass(cmd.getClass());
      Span span = TRACER.spanBuilder(statement).setSpanKind(CLIENT).startSpan();
      DECORATE.afterStart(span);
      DECORATE.onStatement(span, statement);
      return new SpanWithScope(span, currentContextWith(span));
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Enter final SpanWithScope scope,
        @Advice.Thrown final Throwable throwable,
        @Advice.FieldValue("executionContext") final ExecutionContext ctx,
        @Advice.Return(readOnly = false) final Future<Object> responseFuture) {

      Span span = scope.getSpan();

      if (throwable == null) {
        responseFuture.onComplete(new OnCompleteHandler(span), ctx);
      } else {
        DECORATE.onError(span, throwable);
        DECORATE.beforeFinish(span);
        span.end();
      }
      scope.closeScope();
      // span finished in OnCompleteHandler
    }
  }

  public static class OnCompleteHandler extends AbstractFunction1<Try<Object>, Void> {
    private final Span span;

    public OnCompleteHandler(final Span span) {
      this.span = span;
    }

    @Override
    public Void apply(final Try<Object> result) {
      try {
        if (result.isFailure()) {
          DECORATE.onError(span, result.failed().get());
        }
        DECORATE.beforeFinish(span);
      } finally {
        span.end();
      }
      return null;
    }
  }
}
