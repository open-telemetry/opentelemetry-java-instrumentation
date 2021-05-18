/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cxf;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.cxf.jaxws.EndpointImpl;

public class EndpointImplTypeInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.cxf.jaxws.EndpointImpl");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("getServer").and(takesArgument(0, named(String.class.getName()))),
        EndpointImplTypeInstrumentation.class.getName() + "$GetServerAdvice");
  }

  public static class GetServerAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(@Advice.This EndpointImpl endpoint) {
      if (endpoint.isPublished()) {
        return;
      }
      endpoint.getInInterceptors().add(new TracingStartInInterceptor());
      endpoint.getInInterceptors().add(new TracingEndInInterceptor());
      endpoint.getOutFaultInterceptors().add(new TracingOutFaultInterceptor());
    }
  }
}
