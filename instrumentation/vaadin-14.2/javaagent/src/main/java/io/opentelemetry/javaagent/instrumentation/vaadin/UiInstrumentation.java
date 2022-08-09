/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vaadin;

import static io.opentelemetry.javaagent.instrumentation.vaadin.VaadinSingletons.helper;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.vaadin.flow.component.UI;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

// update server span name to route of current view
public class UiInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.vaadin.flow.component.UI");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    // setCurrent is called by some request handler when they have accepted the request
    // we can get the path of currently active route from ui
    transformer.applyAdviceToMethod(
        named("setCurrent").and(takesArgument(0, named("com.vaadin.flow.component.UI"))),
        this.getClass().getName() + "$SetCurrentAdvice");
  }

  @SuppressWarnings("unused")
  public static class SetCurrentAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(@Advice.Argument(0) UI ui) {
      helper().updateServerSpanName(ui);
    }
  }
}
