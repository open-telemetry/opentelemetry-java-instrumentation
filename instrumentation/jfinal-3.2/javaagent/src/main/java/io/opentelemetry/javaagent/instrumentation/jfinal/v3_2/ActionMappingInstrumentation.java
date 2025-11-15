/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jfinal.v3_2;

import static net.bytebuddy.matcher.ElementMatchers.named;

import com.jfinal.core.Action;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class ActionMappingInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.jfinal.core.ActionMapping");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("getAction"), this.getClass().getName() + "$GetActionAdvice");
  }

  @SuppressWarnings("unused")
  public static class GetActionAdvice {

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void exitGetAction(@Advice.Return Action action) {
      JFinalSingletons.updateRoute(action);
    }
  }
}
