/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxws.jws.v1_1;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasSuperMethod;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.methodIsDeclaredByType;
import static io.opentelemetry.javaagent.instrumentation.jaxws.jws.v1_1.JwsSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.inheritsAnnotation;
import static net.bytebuddy.matcher.ElementMatchers.isAnnotatedWith;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.jaxws.common.JaxWsRequest;
import javax.annotation.Nullable;
import javax.jws.WebService;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class JwsAnnotationsInstrumentation implements TypeInstrumentation {

  public static final String JWS_WEB_SERVICE_ANNOTATION = "javax.jws.WebService";

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed(JWS_WEB_SERVICE_ANNOTATION);
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(isAnnotatedWith(named(JWS_WEB_SERVICE_ANNOTATION)))
        .or(isAnnotatedWith(named(JWS_WEB_SERVICE_ANNOTATION)));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    // JaxWS WebService methods are defined either by implementing an interface annotated
    // with @WebService or by any public method from a class annotated with @WebService.
    transformer.applyAdviceToMethod(
        isMethod()
            .and(isPublic())
            .and(not(isStatic()))
            .and(
                hasSuperMethod(
                    methodIsDeclaredByType(inheritsAnnotation(named(JWS_WEB_SERVICE_ANNOTATION))))),
        JwsAnnotationsInstrumentation.class.getName() + "$JwsAnnotationsAdvice");
  }

  @SuppressWarnings("unused")
  public static class JwsAnnotationsAdvice {

    public static class AdviceScope {
      private final CallDepth callDepth;
      private final JaxWsRequest request;
      private final Context context;
      private final Scope scope;

      private AdviceScope(CallDepth callDepth, JaxWsRequest request, Context context, Scope scope) {
        this.callDepth = callDepth;
        this.request = request;
        this.context = context;
        this.scope = scope;
      }

      public static AdviceScope start(CallDepth callDepth, Object target, String methodName) {
        if (callDepth.getAndIncrement() > 0) {
          return new AdviceScope(callDepth, null, null, null);
        }
        Context parentContext = currentContext();
        JaxWsRequest request = new JaxWsRequest(target.getClass(), methodName);
        if (!instrumenter().shouldStart(parentContext, request)) {
          return new AdviceScope(callDepth, null, null, null);
        }
        Context context = instrumenter().start(parentContext, request);
        return new AdviceScope(callDepth, request, context, context.makeCurrent());
      }

      public void end(Throwable throwable) {
        if (callDepth.decrementAndGet() > 0 || scope == null) {
          return;
        }
        scope.close();
        instrumenter().end(context, request, null, throwable);
      }
    }

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AdviceScope startSpan(
        @Advice.This Object target, @Advice.Origin("#m") String methodName) {
      CallDepth callDepth = CallDepth.forClass(WebService.class);
      return AdviceScope.start(callDepth, target, methodName);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Thrown @Nullable Throwable throwable, @Advice.Enter AdviceScope adviceScope) {
      adviceScope.end(throwable);
    }
  }
}
