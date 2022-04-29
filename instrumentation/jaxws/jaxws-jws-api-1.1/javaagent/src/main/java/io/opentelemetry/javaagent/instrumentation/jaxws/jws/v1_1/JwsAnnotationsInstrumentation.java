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
import static io.opentelemetry.javaagent.instrumentation.jaxws.common.JaxWsSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.inheritsAnnotation;
import static net.bytebuddy.matcher.ElementMatchers.isAnnotatedWith;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.jaxws.common.JaxWsRequest;
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
            .and(
                hasSuperMethod(
                    methodIsDeclaredByType(inheritsAnnotation(named(JWS_WEB_SERVICE_ANNOTATION))))),
        JwsAnnotationsInstrumentation.class.getName() + "$JwsAnnotationsAdvice");
  }

  @SuppressWarnings("unused")
  public static class JwsAnnotationsAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void startSpan(
        @Advice.This Object target,
        @Advice.Origin("#m") String methodName,
        @Advice.Local("otelCallDepth") CallDepth callDepth,
        @Advice.Local("otelRequest") JaxWsRequest request,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      callDepth = CallDepth.forClass(WebService.class);
      if (callDepth.getAndIncrement() > 0) {
        return;
      }

      Context parentContext = currentContext();
      request = new JaxWsRequest(target.getClass(), methodName);
      if (!instrumenter().shouldStart(parentContext, request)) {
        return;
      }

      context = instrumenter().start(parentContext, request);
      scope = context.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelCallDepth") CallDepth callDepth,
        @Advice.Local("otelRequest") JaxWsRequest request,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      if (callDepth.decrementAndGet() > 0 || scope == null) {
        return;
      }

      scope.close();
      instrumenter().end(context, request, null, throwable);
    }
  }
}
