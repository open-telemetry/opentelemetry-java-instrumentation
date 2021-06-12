/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkaactor;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import akka.dispatch.Envelope;
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

public class AkkaDispatcherInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("akka.dispatch.Dispatcher");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("dispatch")
            .and(takesArgument(0, named("akka.actor.ActorCell")))
            .and(takesArgument(1, named("akka.dispatch.Envelope"))),
        AkkaDispatcherInstrumentation.class.getName() + "$DispatchEnvelopeAdvice");
  }

  @SuppressWarnings("unused")
  public static class DispatchEnvelopeAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static State enterDispatch(@Advice.Argument(1) Envelope envelope) {
      if (ExecutorInstrumentationUtils.shouldAttachStateToTask(envelope)) {
        ContextStore<Envelope, State> contextStore =
            InstrumentationContext.get(Envelope.class, State.class);
        return ExecutorInstrumentationUtils.setupState(
            contextStore, envelope, Java8BytecodeBridge.currentContext());
      }
      return null;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void exitDispatch(@Advice.Enter State state, @Advice.Thrown Throwable throwable) {
      ExecutorInstrumentationUtils.cleanUpOnMethodExit(state, throwable);
    }
  }
}
