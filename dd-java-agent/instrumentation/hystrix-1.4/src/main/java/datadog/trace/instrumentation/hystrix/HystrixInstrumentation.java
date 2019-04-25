package datadog.trace.instrumentation.hystrix;

import static datadog.trace.agent.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static datadog.trace.instrumentation.hystrix.HystrixDecorator.DECORATE;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;

import com.google.auto.service.AutoService;
import com.netflix.hystrix.HystrixInvokableInfo;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.context.TraceScope;
import io.opentracing.Scope;
import io.opentracing.ScopeManager;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.util.GlobalTracer;
import java.util.HashMap;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import rx.DDTracingUtil;
import rx.Observable;
import rx.Subscriber;

@AutoService(Instrumenter.class)
public class HystrixInstrumentation extends Instrumenter.Default {

  private static final String OPERATION_NAME = "hystrix.cmd";

  public HystrixInstrumentation() {
    super("hystrix");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return safeHasSuperType(
        named("com.netflix.hystrix.HystrixCommand")
            .or(named("com.netflix.hystrix.HystrixObservableCommand")));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "rx.DDTracingUtil",
      "datadog.trace.agent.decorator.BaseDecorator",
      packageName + ".HystrixDecorator",
      packageName + ".HystrixInstrumentation$TracedSubscriber",
      packageName + ".HystrixInstrumentation$TracedOnSubscribe",
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    final Map<ElementMatcher.Junction<MethodDescription>, String> transformers = new HashMap<>();
    transformers.put(
        named("getExecutionObservable").and(returns(named("rx.Observable"))),
        ExecuteAdvice.class.getName());
    transformers.put(
        named("getFallbackObservable").and(returns(named("rx.Observable"))),
        FallbackAdvice.class.getName());
    return transformers;
  }

  public static class ExecuteAdvice {

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.This final HystrixInvokableInfo<?> command,
        @Advice.Return(readOnly = false) Observable result,
        @Advice.Thrown final Throwable throwable) {
      final Observable.OnSubscribe<?> onSubscribe = DDTracingUtil.extractOnSubscribe(result);
      result =
          Observable.create(
              new TracedOnSubscribe(
                  onSubscribe, command, "execute", GlobalTracer.get().scopeManager().active()));
    }
  }

  public static class FallbackAdvice {

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.This final HystrixInvokableInfo<?> command,
        @Advice.Return(readOnly = false) Observable<?> result,
        @Advice.Thrown final Throwable throwable) {
      final Observable.OnSubscribe<?> onSubscribe = DDTracingUtil.extractOnSubscribe(result);
      result =
          Observable.create(
              new TracedOnSubscribe(
                  onSubscribe, command, "fallback", GlobalTracer.get().scopeManager().active()));
    }
  }

  public static class TracedOnSubscribe<T> implements Observable.OnSubscribe<T> {

    private final Observable.OnSubscribe<?> delegate;
    private final HystrixInvokableInfo<?> command;
    private final String methodName;
    private final TraceScope.Continuation continuation;

    public TracedOnSubscribe(
        final Observable.OnSubscribe<?> delegate,
        final HystrixInvokableInfo<?> command,
        final String methodName,
        final Scope parentScope) {
      this.delegate = delegate;
      this.command = command;
      this.methodName = methodName;
      continuation =
          parentScope instanceof TraceScope ? ((TraceScope) parentScope).capture() : null;
    }

    @Override
    public void call(final Subscriber<? super T> subscriber) {
      final Tracer tracer = GlobalTracer.get();
      final Span span; // span finished by TracedSubscriber
      if (continuation != null) {
        try (final TraceScope scope = continuation.activate()) {
          span = tracer.buildSpan(OPERATION_NAME).start();
        }
      } else {
        span = tracer.buildSpan(OPERATION_NAME).start();
      }
      DECORATE.afterStart(span);
      DECORATE.onCommand(span, command, methodName);

      try (final Scope scope = tracer.scopeManager().activate(span, false)) {
        delegate.call(new TracedSubscriber(span, subscriber));
      }
    }
  }

  public static class TracedSubscriber<T> extends Subscriber<T> {

    private final ScopeManager scopeManager = GlobalTracer.get().scopeManager();
    private final Span span;
    private final Subscriber<T> delegate;

    public TracedSubscriber(final Span span, final Subscriber<T> delegate) {
      this.span = span;
      this.delegate = delegate;
    }

    @Override
    public void onStart() {
      try (final Scope scope = scopeManager.activate(span, false)) {
        if (scope instanceof TraceScope) {
          ((TraceScope) scope).setAsyncPropagation(true);
        }
        delegate.onStart();
      }
    }

    @Override
    public void onNext(final T value) {
      try (final Scope scope = scopeManager.activate(span, false)) {
        if (scope instanceof TraceScope) {
          ((TraceScope) scope).setAsyncPropagation(true);
        }
        delegate.onNext(value);
      } catch (final Throwable e) {
        onError(e);
      }
    }

    @Override
    public void onCompleted() {
      boolean errored = false;
      try (final Scope scope = scopeManager.activate(span, false)) {
        if (scope instanceof TraceScope) {
          ((TraceScope) scope).setAsyncPropagation(true);
        }
        delegate.onCompleted();
      } catch (final Throwable e) {
        onError(e);
        errored = true;
      } finally {
        // finish called by onError, so don't finish again.
        if (!errored) {
          DECORATE.beforeFinish(span);
          span.finish();
        }
      }
    }

    @Override
    public void onError(final Throwable e) {
      try (final Scope scope = scopeManager.activate(span, false)) {
        if (scope instanceof TraceScope) {
          ((TraceScope) scope).setAsyncPropagation(true);
        }
        DECORATE.onError(span, e);
        delegate.onError(e);
      } catch (final Throwable e2) {
        DECORATE.onError(span, e2);
        // This recursive call might be dangerous... not sure what the best response is.
        onError(e2);
      } finally {
        DECORATE.beforeFinish(span);
        span.finish();
      }
    }
  }
}
