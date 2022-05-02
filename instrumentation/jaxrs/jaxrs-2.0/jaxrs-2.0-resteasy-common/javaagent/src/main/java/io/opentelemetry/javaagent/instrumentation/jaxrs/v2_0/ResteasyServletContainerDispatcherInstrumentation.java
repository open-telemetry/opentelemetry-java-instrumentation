/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrs.v2_0;

import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.bootstrap.jaxrs.JaxrsContextPath;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class ResteasyServletContainerDispatcherInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.jboss.resteasy.plugins.server.servlet.ServletContainerDispatcher");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod().and(named("service")),
        ResteasyServletContainerDispatcherInstrumentation.class.getName() + "$ServiceAdvice");
  }

  @SuppressWarnings("unused")
  public static class ServiceAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.FieldValue("servletMappingPrefix") String servletMappingPrefix,
        @Advice.Local("otelScope") Scope scope) {
      Context context =
          JaxrsContextPath.init(Java8BytecodeBridge.currentContext(), servletMappingPrefix);
      if (context != null) {
        scope = context.makeCurrent();
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(@Advice.Local("otelScope") Scope scope) {
      if (scope != null) {
        scope.close();
      }
    }
  }
}
