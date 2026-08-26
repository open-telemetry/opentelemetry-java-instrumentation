/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkoremote.v1_0;

import static io.opentelemetry.javaagent.instrumentation.pekkoremote.v1_0.VirtualFields.OUTBOUND_ENVELOPE_CONTEXT;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.pekko.remote.artery.InboundEnvelope;
import org.apache.pekko.remote.artery.OutboundEnvelope;

/**
 * Makes the envelope that is being (de)serialized available to {@link OtelRemoteInstrument}, which
 * pekko calls with the message only.
 */
class RemoteInstrumentsSerializationInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.pekko.remote.artery.RemoteInstruments");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("serialize").and(takesArgument(1, named("java.nio.ByteBuffer"))),
        getClass().getName() + "$SerializeAdvice");
    transformer.applyAdviceToMethod(
        named("deserialize")
            .and(takesArgument(0, named("org.apache.pekko.remote.artery.InboundEnvelope"))),
        getClass().getName() + "$DeserializeAdvice");
  }

  @SuppressWarnings("unused")
  public static class SerializeAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static void onEnter(@Advice.Argument(0) Object outboundEnvelope) {
      // pekko passes an OptionVal, a value class that erases to the envelope, null when empty
      if (outboundEnvelope instanceof OutboundEnvelope) {
        Context context = OUTBOUND_ENVELOPE_CONTEXT.get((OutboundEnvelope) outboundEnvelope);
        RemoteMessageState.startWrite(context);
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit() {
      RemoteMessageState.endWrite();
    }
  }

  @SuppressWarnings("unused")
  public static class DeserializeAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static void onEnter(@Advice.Argument(0) InboundEnvelope inboundEnvelope) {
      RemoteMessageState.startRead(inboundEnvelope);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit() {
      RemoteMessageState.endRead();
    }
  }
}
