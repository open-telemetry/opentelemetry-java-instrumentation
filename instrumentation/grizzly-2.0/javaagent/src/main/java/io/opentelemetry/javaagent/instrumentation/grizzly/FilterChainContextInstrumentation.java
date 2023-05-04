/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.grizzly;

import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class FilterChainContextInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.glassfish.grizzly.filterchain.FilterChainContext");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("resume"), FilterChainContextInstrumentation.class.getName() + "$ResumeAdvice");
  }

  @SuppressWarnings("unused")
  public static class ResumeAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Scope onEnter() {
      return Java8BytecodeBridge.rootContext().makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(@Advice.Enter Scope scope) {
      if (scope != null) {
        scope.close();
      }
    }
  }
}
