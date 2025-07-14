/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrs.v3_0;

import static io.opentelemetry.javaagent.instrumentation.jaxrs.v3_0.ResteasySingletons.INVOKER_NAME;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.jboss.resteasy.core.ResourceMethodInvoker;

public class ResteasyResourceMethodInvokerInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.jboss.resteasy.core.ResourceMethodInvoker");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("invokeOnTarget")
            .and(takesArgument(0, named("org.jboss.resteasy.spi.HttpRequest")))
            .and(takesArgument(1, named("org.jboss.resteasy.spi.HttpResponse")))
            .and(takesArgument(2, Object.class)),
        ResteasyResourceMethodInvokerInstrumentation.class.getName() + "$InvokeOnTargetAdvice");
  }

  @SuppressWarnings("unused")
  public static class InvokeOnTargetAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(@Advice.This ResourceMethodInvoker resourceInvoker) {

      String name = INVOKER_NAME.get(resourceInvoker);
      ResteasySpanName.INSTANCE.updateServerSpanName(Java8BytecodeBridge.currentContext(), name);
    }
  }
}
