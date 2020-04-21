package datadog.trace.instrumentation.rediscala;

import static datadog.trace.agent.tooling.bytebuddy.matcher.DDElementMatchers.safeHasSuperType;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activeScope;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.startSpan;
import static datadog.trace.instrumentation.rediscala.RediscalaClientDecorator.DECORATE;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.bootstrap.instrumentation.api.AgentScope;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import datadog.trace.context.TraceScope;
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
  public ElementMatcher<TypeDescription> typeMatcher() {
    return safeHasSuperType(named("redis.ActorRequest"))
        .or(safeHasSuperType(named("redis.Request")))
        .or(safeHasSuperType(named("redis.BufferedRequest")))
        .or(safeHasSuperType(named("redis.RoundRobinPoolRequest")));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      RediscalaInstrumentation.class.getName() + "$OnCompleteHandler",
      "datadog.trace.bootstrap.instrumentation.decorator.BaseDecorator",
      "datadog.trace.bootstrap.instrumentation.decorator.ClientDecorator",
      "datadog.trace.bootstrap.instrumentation.decorator.DatabaseClientDecorator",
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
    public static AgentScope onEnter(@Advice.Argument(0) final RedisCommand cmd) {
      final AgentSpan span = startSpan("redis.command");
      DECORATE.afterStart(span);
      DECORATE.onStatement(span, cmd.getClass().getName());
      return activateSpan(span, true);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Enter final AgentScope scope,
        @Advice.Thrown final Throwable throwable,
        @Advice.FieldValue("executionContext") final ExecutionContext ctx,
        @Advice.Return(readOnly = false) final Future<Object> responseFuture) {

      final AgentSpan span = scope.span();

      if (throwable == null) {
        responseFuture.onComplete(new OnCompleteHandler(span), ctx);
      } else {
        DECORATE.onError(span, throwable);
        DECORATE.beforeFinish(span);
        span.finish();
      }
      scope.close();
    }
  }

  public static class OnCompleteHandler extends AbstractFunction1<Try<Object>, Void> {
    private final AgentSpan span;

    public OnCompleteHandler(final AgentSpan span) {
      this.span = span;
    }

    @Override
    public Void apply(final Try<Object> result) {
      try {
        if (result.isFailure()) {
          DECORATE.onError(span, result.failed().get());
        }
        DECORATE.beforeFinish(span);
        final TraceScope scope = activeScope();
        if (scope != null) {
          scope.setAsyncPropagation(false);
        }
      } finally {
        span.finish();
      }
      return null;
    }
  }
}
