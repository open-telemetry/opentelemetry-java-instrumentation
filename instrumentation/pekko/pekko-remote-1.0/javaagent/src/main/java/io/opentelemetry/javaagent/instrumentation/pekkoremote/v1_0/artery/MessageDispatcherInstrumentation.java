/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkoremote.v1_0.artery;

import static io.opentelemetry.javaagent.instrumentation.pekkoremote.v1_0.artery.VirtualFields.INBOUND_ENVELOPE_CONTEXT;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.pekko.remote.artery.InboundEnvelope;

/**
 * Makes the context that was received with a remote message current while the message is handed to
 * the actor it is addressed to. The pekko-actor instrumentation takes it from there, it attaches
 * the current context to the envelope that the message is delivered with.
 */
class MessageDispatcherInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.pekko.remote.artery.MessageDispatcher");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("dispatch")
            .and(takesArgument(0, named("org.apache.pekko.remote.artery.InboundEnvelope"))),
        getClass().getName() + "$DispatchAdvice");
  }

  @SuppressWarnings("unused")
  public static class DispatchAdvice {

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Scope onEnter(@Advice.Argument(0) InboundEnvelope envelope) {
      Context context = INBOUND_ENVELOPE_CONTEXT.get(envelope);
      return context == null ? null : context.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.Enter @Nullable Scope scope) {
      if (scope != null) {
        scope.close();
      }
    }
  }
}
