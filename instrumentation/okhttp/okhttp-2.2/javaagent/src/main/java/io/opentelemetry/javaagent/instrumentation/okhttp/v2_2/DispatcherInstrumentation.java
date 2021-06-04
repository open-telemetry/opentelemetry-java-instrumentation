/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.okhttp.v2_2;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.api.ContextStore;
import io.opentelemetry.javaagent.instrumentation.api.InstrumentationContext;
import io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge;
import io.opentelemetry.javaagent.instrumentation.api.concurrent.ExecutorInstrumentationUtils;
import io.opentelemetry.javaagent.instrumentation.api.concurrent.State;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class DispatcherInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.squareup.okhttp.Dispatcher");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("enqueue").and(takesArgument(0, named("com.squareup.okhttp.Call$AsyncCall"))),
        DispatcherInstrumentation.class.getName() + "$AttachStateAdvice");
  }

  public static class AttachStateAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static State onEnter(@Advice.Argument(0) Runnable call) {
      if (ExecutorInstrumentationUtils.shouldAttachStateToTask(call)) {
        ContextStore<Runnable, State> contextStore =
            InstrumentationContext.get(Runnable.class, State.class);
        return ExecutorInstrumentationUtils.setupState(
            contextStore, call, Java8BytecodeBridge.currentContext());
      }
      return null;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(@Advice.Enter State state, @Advice.Thrown Throwable throwable) {
      ExecutorInstrumentationUtils.cleanUpOnMethodExit(state, throwable);
    }
  }
}
