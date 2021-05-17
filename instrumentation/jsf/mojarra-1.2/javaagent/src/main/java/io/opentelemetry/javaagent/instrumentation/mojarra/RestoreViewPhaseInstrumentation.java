/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mojarra;

import static io.opentelemetry.javaagent.instrumentation.mojarra.MojarraTracer.tracer;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge;
import javax.faces.context.FacesContext;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class RestoreViewPhaseInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.sun.faces.lifecycle.RestoreViewPhase");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("execute").and(takesArgument(0, named("javax.faces.context.FacesContext"))),
        RestoreViewPhaseInstrumentation.class.getName() + "$ExecuteAdvice");
  }

  public static class ExecuteAdvice {

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(@Advice.Argument(0) FacesContext facesContext) {
      tracer().updateServerSpanName(Java8BytecodeBridge.currentContext(), facesContext);
    }
  }
}
