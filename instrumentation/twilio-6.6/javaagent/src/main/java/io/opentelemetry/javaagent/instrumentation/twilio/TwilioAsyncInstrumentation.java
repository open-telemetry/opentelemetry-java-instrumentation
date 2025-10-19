/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.twilio;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.extendsClass;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.instrumentation.twilio.TwilioSingletons.instrumenter;
import static io.opentelemetry.javaagent.instrumentation.twilio.TwilioSingletons.spanName;
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
import javax.annotation.Nullable;
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

    public static class AdviceScope {
      private final Context context;
      private final Scope scope;
      private final String spanName;

      private AdviceScope(Context context, Scope scope, String spanName) {
        this.context = context;
        this.scope = scope;
        this.spanName = spanName;
      }

      @Nullable
      public static AdviceScope start(Object target, String methodName) {
        Context parentContext = Context.current();
        String spanName = spanName(target, methodName);
        if (!instrumenter().shouldStart(parentContext, spanName)) {
          return null;
        }

        Context context = instrumenter().start(parentContext, spanName);
        context = TwilioAsyncMarker.markAsync(context);
        return new AdviceScope(context, context.makeCurrent(), spanName);
      }

      public void end(Throwable throwable, ListenableFuture<?> response) {
        scope.close();
        if (throwable != null) {
          // There was a synchronous error,
          // which means we shouldn't wait for a callback to close the span.
          instrumenter().end(context, spanName, null, throwable);
        } else {
          // We're calling an async operation, we still need to finish the span when it's
          // complete and report the results; set an appropriate callback
          Futures.addCallback(
              response,
              new SpanFinishingCallback<>(context, spanName),
              Twilio.getExecutorService());
        }
      }
    }

    /** Method entry instrumentation. */
    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AdviceScope methodEnter(
        @Advice.This Object that, @Advice.Origin("#m") String methodName) {
      return AdviceScope.start(that, methodName);
    }

    /** Method exit instrumentation. */
    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter @Nullable AdviceScope adviceScope,
        @Advice.Return ListenableFuture<?> response) {
      if (adviceScope != null) {
        adviceScope.end(throwable, response);
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
