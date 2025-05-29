/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.okhttp.v2_2;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.bootstrap.executors.ExecutorAdviceHelper;
import io.opentelemetry.javaagent.bootstrap.executors.PropagatedContext;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
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

  @SuppressWarnings("unused")
  public static class AttachStateAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static PropagatedContext onEnter(@Advice.Argument(0) Runnable call) {
      Context context = Java8BytecodeBridge.currentContext();
      if (ExecutorAdviceHelper.shouldPropagateContext(context, call)) {
        VirtualField<Runnable, PropagatedContext> virtualField =
            VirtualField.find(Runnable.class, PropagatedContext.class);
        return ExecutorAdviceHelper.attachContextToTask(context, virtualField, call);
      }
      return null;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.Argument(0) Runnable call,
        @Advice.Enter PropagatedContext propagatedContext,
        @Advice.Thrown Throwable throwable) {
      VirtualField<Runnable, PropagatedContext> virtualField =
          VirtualField.find(Runnable.class, PropagatedContext.class);
      ExecutorAdviceHelper.cleanUpAfterSubmit(propagatedContext, throwable, virtualField, call);
    }
  }
}
