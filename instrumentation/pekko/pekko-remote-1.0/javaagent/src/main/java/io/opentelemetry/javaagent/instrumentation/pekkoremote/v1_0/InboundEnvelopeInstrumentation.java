/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkoremote.v1_0;

import static io.opentelemetry.javaagent.instrumentation.pekkoremote.v1_0.VirtualFields.INBOUND_ENVELOPE_CONTEXT;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.pekko.remote.artery.InboundEnvelope;

/**
 * Inbound envelopes are pooled, the context of the message that an envelope was used for before
 * must not be delivered with the message that it is used for next. The context of the current
 * message is attached while it is deserialized, which happens after the envelope is initialized.
 */
class InboundEnvelopeInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.pekko.remote.artery.ReusableInboundEnvelope");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("init").and(takesArguments(9)), getClass().getName() + "$ClearContextAdvice");
    transformer.applyAdviceToMethod(
        named("clear").and(takesArguments(0)), getClass().getName() + "$ClearContextAdvice");
  }

  @SuppressWarnings("unused")
  public static class ClearContextAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static void onEnter(@Advice.This InboundEnvelope envelope) {
      INBOUND_ENVELOPE_CONTEXT.set(envelope, null);
    }
  }
}
