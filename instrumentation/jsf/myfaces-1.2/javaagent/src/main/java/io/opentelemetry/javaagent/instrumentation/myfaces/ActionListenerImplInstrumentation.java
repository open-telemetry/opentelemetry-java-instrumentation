/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.myfaces;

import static io.opentelemetry.javaagent.instrumentation.myfaces.MyFacesTracer.tracer;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.Map;
import javax.faces.event.ActionEvent;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class ActionListenerImplInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    return named("org.apache.myfaces.application.ActionListenerImpl");
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        named("processAction"),
        ActionListenerImplInstrumentation.class.getName() + "$ProcessActionAdvice");
  }

  public static class ProcessActionAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(0) ActionEvent event,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      context = tracer().startSpan(event);
      if (context != null) {
        scope = context.makeCurrent();
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      if (scope == null) {
        return;
      }
      scope.close();
      if (throwable != null) {
        tracer().endExceptionally(context, throwable);
      } else {
        tracer().end(context);
      }
    }
  }
}
