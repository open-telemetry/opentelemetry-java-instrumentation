/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrs.v2_0;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.bootstrap.jaxrs.JaxrsContextPath;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.jboss.resteasy.core.ResourceLocatorInvoker;

public class ResteasyResourceLocatorInvokerInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.jboss.resteasy.core.ResourceLocatorInvoker");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("invokeOnTargetObject")
            .and(takesArgument(0, named("org.jboss.resteasy.spi.HttpRequest")))
            .and(takesArgument(1, named("org.jboss.resteasy.spi.HttpResponse")))
            .and(takesArgument(2, Object.class)),
        ResteasyResourceLocatorInvokerInstrumentation.class.getName()
            + "$InvokeOnTargetObjectAdvice");
  }

  @SuppressWarnings("unused")
  public static class InvokeOnTargetObjectAdvice {

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Scope onEnter(@Advice.This ResourceLocatorInvoker resourceInvoker) {

      Context currentContext = Java8BytecodeBridge.currentContext();

      String name = ResteasySingletons.LOCATOR_NAME.get(resourceInvoker);
      ResteasySpanName.INSTANCE.updateServerSpanName(currentContext, name);

      // subresource locator returns a resources class that may have @Path annotations
      // append current path to jax-rs context path so that it would be present in the final path
      Context context =
          JaxrsContextPath.init(currentContext, JaxrsContextPath.prepend(currentContext, name));
      return context != null ? context.makeCurrent() : null;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(@Advice.Enter @Nullable Scope scope) {
      if (scope != null) {
        scope.close();
      }
    }
  }
}
