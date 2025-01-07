/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkoactor.v1_0;

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
import org.apache.pekko.dispatch.Envelope;

public class PekkoDispatcherInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.pekko.dispatch.Dispatcher");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("dispatch")
            .and(takesArgument(0, named("org.apache.pekko.actor.ActorCell")))
            .and(takesArgument(1, named("org.apache.pekko.dispatch.Envelope"))),
        PekkoDispatcherInstrumentation.class.getName() + "$DispatchEnvelopeAdvice");
  }

  @SuppressWarnings("unused")
  public static class DispatchEnvelopeAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static PropagatedContext enterDispatch(@Advice.Argument(1) Envelope envelope) {
      Context context = Java8BytecodeBridge.currentContext();
      if (ExecutorAdviceHelper.shouldPropagateContext(context, envelope.message())) {
        VirtualField<Envelope, PropagatedContext> virtualField =
            VirtualField.find(Envelope.class, PropagatedContext.class);
        return ExecutorAdviceHelper.attachContextToTask(context, virtualField, envelope);
      }
      return null;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void exitDispatch(
        @Advice.Argument(1) Envelope envelope,
        @Advice.Enter PropagatedContext propagatedContext,
        @Advice.Thrown Throwable throwable) {
      VirtualField<Envelope, PropagatedContext> virtualField =
          VirtualField.find(Envelope.class, PropagatedContext.class);
      ExecutorAdviceHelper.cleanUpAfterSubmit(propagatedContext, throwable, virtualField, envelope);
    }
  }
}
