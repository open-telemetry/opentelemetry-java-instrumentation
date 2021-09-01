/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.twilio;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.extendsClass;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.instrumentation.twilio.TwilioTracer.instrumenter;
import static io.opentelemetry.javaagent.instrumentation.twilio.TwilioTracer.spanName;
import static net.bytebuddy.matcher.ElementMatchers.isAbstract;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.returns;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.twilio.Twilio;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/** Instrument the Twilio SDK to identify calls as a separate service. */
public class TwilioAsyncInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("com.twilio.Twilio");
  }

  /** Match any child class of the base Twilio service classes. */
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return extendsClass(
        namedOneOf(
            "com.twilio.base.Creator",
            "com.twilio.base.Deleter",
            "com.twilio.base.Fetcher",
            "com.twilio.base.Reader",
            "com.twilio.base.Updater"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    /*
       We are listing out the main service calls on the Creator, Deleter, Fetcher, Reader, and
       Updater abstract classes. The isDeclaredBy() matcher did not work in the unit tests and
       we found that there were certain methods declared on the base class (particularly Reader),
       which we weren't interested in annotating.
    */
    transformer.applyAdviceToMethod(
        isMethod()
            .and(namedOneOf("createAsync", "deleteAsync", "readAsync", "fetchAsync", "updateAsync"))
            .and(isPublic())
            .and(not(isAbstract()))
            .and(returns(named("com.google.common.util.concurrent.ListenableFuture"))),
        TwilioAsyncInstrumentation.class.getName() + "$TwilioClientAsyncAdvice");
  }

  /** Advice for instrumenting Twilio service classes. */
  @SuppressWarnings("unused")
  public static class TwilioClientAsyncAdvice {

    /** Method entry instrumentation. */
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(
        @Advice.This Object that,
        @Advice.Origin("#m") String methodName,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope,
        @Advice.Local("otelSpanName") String spanName) {
      Context parentContext = currentContext();
      spanName = spanName(that, methodName);
      if (!instrumenter().shouldStart(parentContext, spanName)) {
        return;
      }

      context = instrumenter().start(parentContext, spanName);
      scope = context.makeCurrent();
    }

    /** Method exit instrumentation. */
    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Thrown Throwable throwable,
        @Advice.Return ListenableFuture<?> response,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope,
        @Advice.Local("otelSpanName") String spanName) {
      if (scope == null) {
        return;
      }

      scope.close();
      if (throwable != null) {
        // There was an synchronous error,
        // which means we shouldn't wait for a callback to close the span.
        instrumenter().end(context, spanName, null, throwable);
      } else {
        // We're calling an async operation, we still need to finish the span when it's
        // complete and report the results; set an appropriate callback
        Futures.addCallback(
            response, new SpanFinishingCallback<>(context, spanName), Twilio.getExecutorService());
      }
    }
  }

  /**
   * FutureCallback, which automatically finishes the span and annotates with any appropriate
   * metadata on a potential failure.
   */
  public static class SpanFinishingCallback<T> implements FutureCallback<T> {

    /** Span that we should finish and annotate when the future is complete. */
    private final Context context;

    private final String spanName;

    public SpanFinishingCallback(Context context, String spanName) {
      this.context = context;
      this.spanName = spanName;
    }

    @Override
    public void onSuccess(Object result) {
      instrumenter().end(context, spanName, result, null);
    }

    @Override
    public void onFailure(Throwable t) {
      instrumenter().end(context, spanName, null, t);
    }
  }
}
