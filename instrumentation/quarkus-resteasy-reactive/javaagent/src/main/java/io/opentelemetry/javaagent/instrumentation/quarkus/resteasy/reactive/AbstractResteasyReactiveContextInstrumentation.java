/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.quarkus.resteasy.reactive;

import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.jboss.resteasy.reactive.common.core.AbstractResteasyReactiveContext;
import org.jboss.resteasy.reactive.server.core.ResteasyReactiveRequestContext;

public class AbstractResteasyReactiveContextInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.jboss.resteasy.reactive.common.core.AbstractResteasyReactiveContext");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("run"),
        AbstractResteasyReactiveContextInstrumentation.class.getName() + "$RunAdvice");
  }

  @SuppressWarnings("unused")
  public static class RunAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static OtelRequestContext onEnter(
        @Advice.This AbstractResteasyReactiveContext<?, ?> requestContext) {
      if (requestContext instanceof ResteasyReactiveRequestContext) {
        ResteasyReactiveRequestContext context = (ResteasyReactiveRequestContext) requestContext;
        return OtelRequestContext.start(context);
      }
      return null;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(@Advice.Enter OtelRequestContext context) {
      if (context != null) {
        context.close();
      }
    }
  }
}
