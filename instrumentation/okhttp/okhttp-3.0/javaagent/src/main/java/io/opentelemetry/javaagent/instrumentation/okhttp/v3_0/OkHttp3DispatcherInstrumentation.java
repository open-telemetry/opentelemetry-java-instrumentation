/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.okhttp.v3_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.field.VirtualField;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.bootstrap.executors.ExecutorAdviceHelper;
import io.opentelemetry.javaagent.bootstrap.executors.PropagatedContext;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class OkHttp3DispatcherInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("okhttp3.Dispatcher");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("enqueue")
            .or(named("enqueue$okhttp"))
            .and(takesArgument(0, implementsInterface(named(Runnable.class.getName())))),
        OkHttp3DispatcherInstrumentation.class.getName() + "$AttachStateAdvice");
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
        @Advice.Enter PropagatedContext propagatedContext, @Advice.Thrown Throwable throwable) {
      ExecutorAdviceHelper.cleanUpAfterSubmit(propagatedContext, throwable);
    }
  }
}
