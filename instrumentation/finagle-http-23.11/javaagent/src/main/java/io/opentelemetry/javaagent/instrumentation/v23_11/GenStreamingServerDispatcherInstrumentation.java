/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.v23_11;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class GenStreamingServerDispatcherInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return hasSuperType(named("com.twitter.finagle.http.GenStreamingSerialServerDispatcher"));
  }

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("com.twitter.finagle.http.GenStreamingSerialServerDispatcher");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod().and(named("loop")),
        GenStreamingServerDispatcherInstrumentation.class.getName() + "$LoopAdvice");
  }

  @SuppressWarnings("unused")
  public static class LoopAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter() {
      // this works bc at this point in the server evaluation, the netty
      // instrumentation has already gone to work and assigned the context to the
      // local thread;
      //
      // this works specifically in finagle's netty stack bc at this point the loop()
      // method is running on a netty thread with the necessary access to the
      // java-native ThreadLocal where the Context is stored
      Helpers.CONTEXT_LOCAL.update(Context.current());
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void methodExit(@Advice.Thrown Throwable thrown) {
      // always clear this
      Helpers.CONTEXT_LOCAL.clear();
    }
  }
}
