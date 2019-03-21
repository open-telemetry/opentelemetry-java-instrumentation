package datadog.trace.instrumentation.twilio;

import static datadog.trace.agent.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static datadog.trace.instrumentation.twilio.TwilioClientDecorator.DECORATE;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isAbstract;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;

import com.google.auto.service.AutoService;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.twilio.Twilio;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.bootstrap.CallDepthThreadLocalMap;
import datadog.trace.context.TraceScope;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.util.GlobalTracer;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

/** Instrument the Twilio SDK to identify calls as a seperate service. */
@AutoService(Instrumenter.class)
public class TwilioInstrumentation extends Instrumenter.Default {

  public TwilioInstrumentation() {
    super("twilio-sdk");
  }

  @Override
  public net.bytebuddy.matcher.ElementMatcher<
          ? super net.bytebuddy.description.type.TypeDescription>
      typeMatcher() {
    return safeHasSuperType(
        named("com.twilio.base.Creator")
            .or(named("com.twilio.base.Deleter"))
            .or(named("com.twilio.base.Fetcher"))
            .or(named("com.twilio.base.Reader"))
            .or(named("com.twilio.base.Updater")));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "datadog.trace.agent.decorator.BaseDecorator",
      "datadog.trace.agent.decorator.ClientDecorator",
      packageName + ".TwilioClientDecorator",
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {

    return singletonMap(
        isMethod()
            .and(isPublic())
            .and(not(isAbstract()))
            .and(
                nameStartsWith("create")
                    .or(nameStartsWith("delete"))
                    .or(nameStartsWith("read"))
                    .or(nameStartsWith("fetch"))
                    .or(nameStartsWith("update"))),
        TwilioClientAdvice.class.getName());
  }

  public static class TwilioClientAdvice {

    /** Method entry instrumentation */
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Scope methodEnter(
        @Advice.This final Object that, @Advice.Origin("#m") final String methodName) {

      final int callDepth = CallDepthThreadLocalMap.incrementCallDepth(Twilio.class);
      if (callDepth > 0) {
        return null;
      }

      final boolean isAsync = methodName.endsWith("Async");

      final Tracer tracer = GlobalTracer.get();
      final Scope scope = tracer.buildSpan("twilio.sdk").startActive(!isAsync);
      final Span span = scope.span();

      DECORATE.afterStart(span);
      DECORATE.onServiceExecution(span, that, methodName);

      if (scope instanceof TraceScope) {
        ((TraceScope) scope).setAsyncPropagation(true);
      }

      return scope;
    }

    /** Method exit instrumentation */
    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Enter final Scope scope,
        @Advice.Thrown final Throwable throwable,
        @Advice.Return final Object response,
        @Advice.Origin("#m") final String methodName) {
      if (scope != null) {
        try {
          final boolean isAsync = methodName.endsWith("Async");
          System.err.println("isAsync = " + isAsync);

          final Span span = scope.span();

          DECORATE.onError(span, throwable);
          DECORATE.beforeFinish(span);

          // If we're calling an async operation, we still need to finish the span when it's
          // complete; set an appropriate callback
          if (isAsync && response instanceof ListenableFuture) {
            Futures.addCallback(
                (ListenableFuture) response,
                new SpanFinishingCallback(span),
                Twilio.getExecutorService());
          } else {
            DECORATE.onResult(span, response);
          }

        } finally {
          scope.close();
          CallDepthThreadLocalMap.reset(Twilio.class);
        }
      }
    }
  }

  /**
   * FutureCallback, which automatically finishes the span and annotates with any appropriate
   * metadata on a potential failure.
   */
  private static class SpanFinishingCallback implements FutureCallback {

    private final Span span;

    SpanFinishingCallback(final Span span) {
      this.span = span;
    }

    @Override
    public void onSuccess(final Object result) {
      DECORATE.beforeFinish(span);
      DECORATE.onResult(span, result);
      span.finish();
    }

    @Override
    public void onFailure(final Throwable t) {
      DECORATE.onError(span, t);
      DECORATE.beforeFinish(span);
      span.finish();
    }
  }
}
