/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vaadin;

import static io.opentelemetry.javaagent.instrumentation.vaadin.VaadinSingletons.helper;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.vaadin.flow.router.Location;
import com.vaadin.flow.router.NavigationTrigger;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

// set server span name on initial page load
public class RouterInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.vaadin.flow.router.Router");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("navigate")
            .and(takesArguments(4))
            .and(takesArgument(1, named("com.vaadin.flow.router.Location")))
            .and(takesArgument(2, named("com.vaadin.flow.router.NavigationTrigger"))),
        this.getClass().getName() + "$NavigateAdvice");
  }

  @SuppressWarnings("unused")
  public static class NavigateAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(1) Location location,
        @Advice.Argument(2) NavigationTrigger navigationTrigger) {
      if (navigationTrigger == NavigationTrigger.PAGE_LOAD) {
        helper().updateServerSpanName(location);
      }
    }
  }
}
