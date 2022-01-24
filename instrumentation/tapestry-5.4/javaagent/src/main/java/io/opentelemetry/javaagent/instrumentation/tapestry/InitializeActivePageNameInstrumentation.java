/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.tapestry;

import static io.opentelemetry.instrumentation.api.instrumenter.http.HttpRouteSource.CONTROLLER;
import static io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge.currentContext;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpRouteHolder;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.tapestry5.services.ComponentEventRequestParameters;
import org.apache.tapestry5.services.PageRenderRequestParameters;

public class InitializeActivePageNameInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.tapestry5.services.InitializeActivePageName");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(isPublic())
            .and(named("handleComponentEvent"))
            .and(takesArguments(2))
            .and(
                takesArgument(
                    0, named("org.apache.tapestry5.services.ComponentEventRequestParameters")))
            .and(takesArgument(1, named("org.apache.tapestry5.services.ComponentRequestHandler"))),
        this.getClass().getName() + "$HandleComponentEventAdvice");
    transformer.applyAdviceToMethod(
        isMethod()
            .and(isPublic())
            .and(named("handlePageRender"))
            .and(takesArguments(2))
            .and(
                takesArgument(
                    0, named("org.apache.tapestry5.services.PageRenderRequestParameters")))
            .and(takesArgument(1, named("org.apache.tapestry5.services.ComponentRequestHandler"))),
        this.getClass().getName() + "$HandlePageRenderAdvice");
  }

  @SuppressWarnings("unused")
  public static class HandleComponentEventAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(@Advice.Argument(0) ComponentEventRequestParameters parameters) {
      HttpRouteHolder.updateHttpRoute(
          currentContext(),
          CONTROLLER,
          TapestryServerSpanNaming.SERVER_SPAN_NAME,
          parameters.getActivePageName());
    }
  }

  @SuppressWarnings("unused")
  public static class HandlePageRenderAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(@Advice.Argument(0) PageRenderRequestParameters parameters) {
      HttpRouteHolder.updateHttpRoute(
          currentContext(),
          CONTROLLER,
          TapestryServerSpanNaming.SERVER_SPAN_NAME,
          parameters.getLogicalPageName());
    }
  }
}
