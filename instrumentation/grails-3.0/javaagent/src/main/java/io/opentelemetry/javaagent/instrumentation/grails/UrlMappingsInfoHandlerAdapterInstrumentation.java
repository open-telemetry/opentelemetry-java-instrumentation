/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.grails;

import static io.opentelemetry.instrumentation.api.servlet.ServerSpanNaming.Source.CONTROLLER;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.servlet.ServerSpanNaming;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.grails.web.mapping.mvc.GrailsControllerUrlMappingInfo;

public class UrlMappingsInfoHandlerAdapterInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.grails.web.mapping.mvc.UrlMappingsInfoHandlerAdapter");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(isPublic())
            .and(named("handle"))
            .and(takesArgument(2, named(Object.class.getName())))
            .and(takesArguments(3)),
        UrlMappingsInfoHandlerAdapterInstrumentation.class.getName() + "$ServerSpanNameAdvice");
  }

  @SuppressWarnings("unused")
  public static class ServerSpanNameAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void nameSpan(@Advice.Argument(2) Object handler) {

      if (handler instanceof GrailsControllerUrlMappingInfo) {
        Context parentContext = Java8BytecodeBridge.currentContext();

        ServerSpanNaming.updateServerSpanName(
            parentContext,
            CONTROLLER,
            GrailsServerSpanNaming.SERVER_SPAN_NAME,
            (GrailsControllerUrlMappingInfo) handler);
      }
    }
  }
}
