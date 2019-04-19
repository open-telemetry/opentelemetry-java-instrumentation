package datadog.trace.instrumentation.twilio;

import static datadog.trace.agent.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static datadog.trace.instrumentation.twilio.TwilioClientDecorator.DECORATE;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isAbstract;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.returns;

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
import io.opentracing.util.GlobalTracer;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

/** Instrument the Twilio SDK to identify calls as a seperate service. */
@AutoService(Instrumenter.class)
public class TwilioAsyncInstrumentation extends Instrumenter.Default {

  public TwilioAsyncInstrumentation() {
    super("twilio-sdk");
  }

  /** Match any child class of the base Twilio service classes. */
  @Override
  public ElementMatcher<? super net.bytebuddy.description.type.TypeDescription> typeMatcher() {
    return safeHasSuperType(
        named("com.twilio.base.Creator")
            .or(named("com.twilio.base.Deleter"))
            .or(named("com.twilio.base.Fetcher"))
            .or(named("com.twilio.base.Reader"))
            .or(named("com.twilio.base.Updater")));
  }

  /** Return the helper classes which will be available for use in instrumentation. */
  @Override
  public String[] helperClassNames() {
    return new String[] {
      "datadog.trace.agent.decorator.BaseDecorator",
      "datadog.trace.agent.decorator.ClientDecorator",
      packageName + ".TwilioClientDecorator",
      packageName + ".TwilioAsyncInstrumentation$SpanFinishingCallback",
    };
  }

  /** Return bytebuddy transformers for instrumenting the Twilio SDK. */
  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {

    /*
       We are listing out the main service calls on the Creator, Deleter, Fetcher, Reader, and
       Updater abstract classes. The isDeclaredBy() matcher did not work in the unit tests and
       we found that there were certain methods declared on the base class (particularly Reader),
       which we weren't interested in annotating.
    */

    return singletonMap(
        isMethod()
            .and(isPublic())
            .and(not(isAbstract()))
            .and(
                named("createAsync")
                    .or(named("deleteAsync"))
                    .or(named("readAsync"))
                    .or(named("fetchAsync"))
                    .or(named("updateAsync")))
            .and(returns(named("com.google.common.util.concurrent.ListenableFuture"))),
        TwilioClientAsyncAdvice.class.getName());
  }

  /** Advice for instrumenting Twilio service classes. */
  public static class TwilioClientAsyncAdvice {

    /** Method entry instrumentation. */
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Scope methodEnter(
        @Advice.This final Object that, @Advice.Origin("#m") final String methodName) {

      // Ensure that we only create a span for the top-level Twilio client method; except in the
      // case of async operations where we want visibility into how long the task was delayed from
      // starting. Our call depth checker does not span threads, so the async case is handled
      // automatically for us.
      final int callDepth = CallDepthThreadLocalMap.incrementCallDepth(Twilio.class);
      if (callDepth > 0) {
        return null;
      }

      // Don't automatically close the span with the scope if we're executing an async method
      final Scope scope = GlobalTracer.get().buildSpan("twilio.sdk").startActive(false);
      final Span span = scope.span();

      DECORATE.afterStart(span);
      DECORATE.onServiceExecution(span, that, methodName);

      // If an async operation was invoked and we have a TraceScope,
      if (scope instanceof TraceScope) {
        // Enable async propagation, so the newly spawned task will be associated back with this
        // original trace.
        ((TraceScope) scope).setAsyncPropagation(true);
      }

      return scope;
    }

    /** Method exit instrumentation. */
    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Enter final Scope scope,
        @Advice.Thrown final Throwable throwable,
        @Advice.Return final ListenableFuture response) {

      // If we have a scope (i.e. we were the top-level Twilio SDK invocation),
      if (scope != null) {
        try {
          final Span span = scope.span();

          if (throwable != null) {
            // There was an synchronous error,
            // which means we shouldn't wait for a callback to close the span.
            DECORATE.onError(span, throwable);
            DECORATE.beforeFinish(span);
            span.finish();
          } else {
            // We're calling an async operation, we still need to finish the span when it's
            // complete and report the results; set an appropriate callback
            Futures.addCallback(
                response, new SpanFinishingCallback(span), Twilio.getExecutorService());
          }
        } finally {
          scope.close(); // won't finish the span.
          CallDepthThreadLocalMap.reset(Twilio.class); // reset call depth count
        }
      }
    }
  }

  /**
   * FutureCallback, which automatically finishes the span and annotates with any appropriate
   * metadata on a potential failure.
   */
  public static class SpanFinishingCallback implements FutureCallback {

    /** Span that we should finish and annotate when the future is complete. */
    private final Span span;

    public SpanFinishingCallback(final Span span) {
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
