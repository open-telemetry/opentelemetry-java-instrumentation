/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.v23_11;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import scala.Option;

public class ChannelTransportInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.twitter.finagle.netty4.transport.ChannelTransport");
  }

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("com.twitter.finagle.netty4.transport.ChannelTransport");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod().and(named("write")),
        ChannelTransportInstrumentation.class.getName() + "$WriteAdvice");
  }

  @SuppressWarnings("unused")
  public static class WriteAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(@Advice.Local("otelScope") Scope scope) {
      Option<Context> ref = Helpers.CONTEXT_LOCAL.apply();
      if (ref.isDefined()) {
        scope = ref.get().makeCurrent();
      }
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void methodExit(
        @Advice.Local("otelScope") Scope scope, @Advice.Thrown Throwable thrown) {
      if (scope != null) {
        scope.close();
      }
    }
  }
}
