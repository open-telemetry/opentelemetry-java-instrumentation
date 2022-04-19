/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.twilio;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.extendsClass;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.instrumentation.twilio.TwilioSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isAbstract;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.not;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/** Instrument the Twilio SDK to identify calls as a separate service. */
public class TwilioSyncInstrumentation implements TypeInstrumentation {

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
            .and(isPublic())
            .and(not(isAbstract()))
            .and(namedOneOf("create", "delete", "read", "fetch", "update")),
        TwilioSyncInstrumentation.class.getName() + "$TwilioClientAdvice");
  }

  /** Advice for instrumenting Twilio service classes. */
  @SuppressWarnings("unused")
  public static class TwilioClientAdvice {

    /** Method entry instrumentation. */
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(
        @Advice.This Object that,
        @Advice.Origin("#m") String methodName,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope,
        @Advice.Local("otelSpanName") String spanName) {
      Context parentContext = currentContext();
      spanName = TwilioSingletons.spanName(that, methodName);
      if (!instrumenter().shouldStart(parentContext, spanName)
          || TwilioAsyncMarker.isMarkedAsync(parentContext)) {
        return;
      }

      context = instrumenter().start(parentContext, spanName);
      scope = context.makeCurrent();
    }

    /** Method exit instrumentation. */
    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Thrown Throwable throwable,
        @Advice.Return Object response,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope,
        @Advice.Local("otelSpanName") String spanName) {
      if (scope == null) {
        return;
      }

      scope.close();
      instrumenter().end(context, spanName, response, throwable);
    }
  }
}
