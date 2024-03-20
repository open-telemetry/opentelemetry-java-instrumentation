/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.executors;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.bootstrap.executors.ExecutorAdviceHelper;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class VirtualThreadInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("java.lang.VirtualThread");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    // Disable context propagation when virtual thread is switched to the carrier thread. We should
    // not propagate context on the carrier thread. Also, context propagation code can cause the
    // carrier thread to park when it normally does not park, which may be unexpected for the jvm.
    // https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/10747
    transformer.applyAdviceToMethod(
        named("switchToCarrierThread").and(takesArguments(0)),
        this.getClass().getName() + "$SwitchToCarrierAdvice");
    transformer.applyAdviceToMethod(
        // takes an extra argument in jdk 21 ea versions
        named("switchToVirtualThread").and(takesArguments(1).or(takesArguments(2))),
        this.getClass().getName() + "$SwitchToVirtualAdvice");
  }

  @SuppressWarnings("unused")
  public static class SwitchToCarrierAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void exit() {
      ExecutorAdviceHelper.disablePropagation();
    }
  }

  @SuppressWarnings("unused")
  public static class SwitchToVirtualAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void enter() {
      ExecutorAdviceHelper.enablePropagation();
    }
  }
}
