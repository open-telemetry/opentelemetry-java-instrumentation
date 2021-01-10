/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrs.v2_0;

import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.ClassLoaderMatcher.hasClassesNamed;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.Map;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * This adds the filter class name to the request properties. The class name is used by <code>
 * DefaultRequestContextInstrumentation</code>
 */
public class ContainerRequestFilterInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("javax.ws.rs.container.ContainerRequestFilter");
  }

  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    return implementsInterface(named("javax.ws.rs.container.ContainerRequestFilter"));
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod()
            .and(named("filter"))
            .and(takesArguments(1))
            .and(takesArgument(0, named("javax.ws.rs.container.ContainerRequestContext"))),
        ContainerRequestFilterInstrumentation.class.getName() + "$RequestFilterAdvice");
  }

  public static class RequestFilterAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void setFilterClass(
        @Advice.This ContainerRequestFilter filter,
        @Advice.Argument(0) ContainerRequestContext context) {
      context.setProperty(JaxRsAnnotationsTracer.ABORT_FILTER_CLASS, filter.getClass());
    }
  }
}
