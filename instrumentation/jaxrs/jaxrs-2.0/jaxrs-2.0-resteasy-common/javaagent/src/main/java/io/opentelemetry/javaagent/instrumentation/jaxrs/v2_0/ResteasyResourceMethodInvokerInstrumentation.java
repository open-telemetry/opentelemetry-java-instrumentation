/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrs.v2_0;

import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.instrumentation.api.InstrumentationContext;
import io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.jboss.resteasy.core.ResourceMethodInvoker;

public class ResteasyResourceMethodInvokerInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.jboss.resteasy.core.ResourceMethodInvoker");
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        named("invokeOnTarget")
            .and(takesArgument(0, named("org.jboss.resteasy.spi.HttpRequest")))
            .and(takesArgument(1, named("org.jboss.resteasy.spi.HttpResponse")))
            .and(takesArgument(2, Object.class)),
        ResteasyResourceMethodInvokerInstrumentation.class.getName() + "$InvokeOnTargetAdvice");
  }

  public static class InvokeOnTargetAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(@Advice.This ResourceMethodInvoker resourceInvoker) {

      String name =
          InstrumentationContext.get(ResourceMethodInvoker.class, String.class)
              .get(resourceInvoker);
      ResteasyTracingUtil.updateServerSpanName(Java8BytecodeBridge.currentContext(), name);
    }
  }
}
