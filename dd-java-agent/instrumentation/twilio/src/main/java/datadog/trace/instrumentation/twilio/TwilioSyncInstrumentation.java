package datadog.trace.instrumentation.twilio;

import static datadog.trace.agent.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static datadog.trace.instrumentation.twilio.TwilioClientDecorator.DECORATE;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isAbstract;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;

import com.google.auto.service.AutoService;
import com.twilio.Twilio;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.bootstrap.CallDepthThreadLocalMap;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.util.GlobalTracer;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

/** Instrument the Twilio SDK to identify calls as a seperate service. */
@AutoService(Instrumenter.class)
public class TwilioSyncInstrumentation extends Instrumenter.Default {

  public TwilioSyncInstrumentation() {
    super("twilio-sdk");
  }

  /** Match any child class of the base Twilio service classes. */
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

  /** Return the helper classes which will be available for use in instrumentation. */
  @Override
  public String[] helperClassNames() {
    return new String[] {
      "datadog.trace.agent.decorator.BaseDecorator",
      "datadog.trace.agent.decorator.ClientDecorator",
      packageName + ".TwilioClientDecorator",
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
                named("create")
                    .or(named("delete"))
                    .or(named("read"))
                    .or(named("fetch"))
                    .or(named("update"))),
        TwilioClientAdvice.class.getName());
  }

  /** Advice for instrumenting Twilio service classes. */
  public static class TwilioClientAdvice {

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

      final Scope scope = GlobalTracer.get().buildSpan("twilio.sdk").startActive(true);
      final Span span = scope.span();

      DECORATE.afterStart(span);
      DECORATE.onServiceExecution(span, that, methodName);

      return scope;
    }

    /** Method exit instrumentation. */
    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Enter final Scope scope,
        @Advice.Thrown final Throwable throwable,
        @Advice.Return final Object response) {

      // If we have a scope (i.e. we were the top-level Twilio SDK invocation),
      if (scope != null) {
        try {
          final Span span = scope.span();

          DECORATE.onResult(span, response);
          DECORATE.onError(span, throwable);
          DECORATE.beforeFinish(span);
        } finally {
          scope.close();
          CallDepthThreadLocalMap.reset(Twilio.class); // reset call depth count
        }
      }
    }
  }
}
