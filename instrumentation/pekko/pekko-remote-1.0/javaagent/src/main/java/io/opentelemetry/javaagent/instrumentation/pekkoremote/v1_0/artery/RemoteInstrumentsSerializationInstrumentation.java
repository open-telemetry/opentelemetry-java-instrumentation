/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkoremote.v1_0.artery;

import static io.opentelemetry.javaagent.instrumentation.pekkoremote.v1_0.artery.RemoteMessageState.inboundEnvelope;
import static io.opentelemetry.javaagent.instrumentation.pekkoremote.v1_0.artery.RemoteMessageState.outboundContext;
import static io.opentelemetry.javaagent.instrumentation.pekkoremote.v1_0.artery.VirtualFields.OUTBOUND_ENVELOPE_CONTEXT;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
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
    @Nullable
    public static Context onEnter(@Advice.Argument(0) Object outboundEnvelope) {
      // pekko passes an OptionVal, a value class that erases to the envelope, null when empty
      Context context =
          outboundEnvelope instanceof OutboundEnvelope
              ? OUTBOUND_ENVELOPE_CONTEXT.get((OutboundEnvelope) outboundEnvelope)
              : null;
      return outboundContext().set(context);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.Enter @Nullable Context previous) {
      outboundContext().restore(previous);
    }
  }

  @SuppressWarnings("unused")
  public static class DeserializeAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    @Nullable
    public static InboundEnvelope onEnter(@Advice.Argument(0) InboundEnvelope envelope) {
      return inboundEnvelope().set(envelope);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.Enter @Nullable InboundEnvelope previous) {
      inboundEnvelope().restore(previous);
    }
  }
}
