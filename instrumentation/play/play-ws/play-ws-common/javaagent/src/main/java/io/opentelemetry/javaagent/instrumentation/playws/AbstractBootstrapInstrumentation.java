/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.playws;

import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class AbstractBootstrapInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("play.shaded.ahc.io.netty.bootstrap.AbstractBootstrap");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("initAndRegister"),
        AbstractBootstrapInstrumentation.class.getName() + "$DisablePropagationAdvice");
  }

  @SuppressWarnings("unused")
  public static class DisablePropagationAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Scope onEnter() {
      // Prevent context from leaking by running this method under root context.
      // Root context is not propagated by executor instrumentation.
      if (Java8BytecodeBridge.currentContext() != Java8BytecodeBridge.rootContext()) {
        return Java8BytecodeBridge.rootContext().makeCurrent();
      }
      return null;
    }

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(@Advice.Enter Scope scope) {
      if (scope != null) {
        scope.close();
      }
    }
  }
}
