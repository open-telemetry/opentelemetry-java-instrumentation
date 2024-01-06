/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.v23_11;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.twitter.util.Future;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.lang.invoke.MethodHandle;
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

    @Advice.OnMethodEnter(suppress = Throwable.class, skipOn = Advice.OnDefaultValue.class)
    public static boolean methodEnter() {
      // only call when we've effectively wrapped the method (hence: isRecursed)
      return Helpers.LOOP_GUARD.isRecursed();
    }

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void methodExit(
        @Advice.SelfCallHandle MethodHandle handle,
        @Advice.Return(readOnly = false) Future<?> ret) {
      ret = Helpers.loopAdviceExit(handle, ret);
    }
  }
}
