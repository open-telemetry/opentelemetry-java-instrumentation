/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.grails;

import static io.opentelemetry.javaagent.instrumentation.grails.GrailsTracer.tracer;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.tracer.ServerSpan;
import io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.grails.web.mapping.mvc.GrailsControllerUrlMappingInfo;

public class UrlMappingsInfoHandlerAdapterInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    return named("org.grails.web.mapping.mvc.UrlMappingsInfoHandlerAdapter");
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod()
            .and(isPublic())
            .and(named("handle"))
            .and(takesArgument(2, named(Object.class.getName())))
            .and(takesArguments(3)),
        UrlMappingsInfoHandlerAdapterInstrumentation.class.getName() + "$ServerSpanNameAdvice");
  }

  public static class ServerSpanNameAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void nameSpan(@Advice.Argument(2) Object handler) {

      if (handler instanceof GrailsControllerUrlMappingInfo) {
        Context parentContext = Java8BytecodeBridge.currentContext();
        Span serverSpan = ServerSpan.fromContextOrNull(parentContext);
        if (serverSpan != null) {
          tracer()
              .nameServerSpan(parentContext, serverSpan, (GrailsControllerUrlMappingInfo) handler);
        }
      }
    }
  }
}
