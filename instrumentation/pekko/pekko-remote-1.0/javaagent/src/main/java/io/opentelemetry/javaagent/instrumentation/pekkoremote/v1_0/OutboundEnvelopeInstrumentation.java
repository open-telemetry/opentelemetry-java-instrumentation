/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkoremote.v1_0;

import static io.opentelemetry.javaagent.instrumentation.pekkoremote.v1_0.VirtualFields.OUTBOUND_ENVELOPE_CONTEXT;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.pekko.remote.artery.OutboundEnvelope;

/**
 * Captures the context of the thread that sends a message to a remote actor. Pekko serializes the
 * message later, on a stream thread, where the context of the sender is not available any more.
 */
class OutboundEnvelopeInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.pekko.remote.artery.ReusableOutboundEnvelope");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("init").and(takesArguments(3)), getClass().getName() + "$InitAdvice");
    transformer.applyAdviceToMethod(
        named("clear").and(takesArguments(0)), getClass().getName() + "$ClearAdvice");
  }

  @SuppressWarnings("unused")
  public static class InitAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.This OutboundEnvelope envelope) {
      Context context = Java8BytecodeBridge.currentContext();
      OUTBOUND_ENVELOPE_CONTEXT.set(
          envelope, context == Java8BytecodeBridge.rootContext() ? null : context);
    }
  }

  @SuppressWarnings("unused")
  public static class ClearAdvice {

    // envelopes are pooled, a stale context must not be attached to the next message that uses this
    // envelope
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.This OutboundEnvelope envelope) {
      OUTBOUND_ENVELOPE_CONTEXT.set(envelope, null);
    }
  }
}
