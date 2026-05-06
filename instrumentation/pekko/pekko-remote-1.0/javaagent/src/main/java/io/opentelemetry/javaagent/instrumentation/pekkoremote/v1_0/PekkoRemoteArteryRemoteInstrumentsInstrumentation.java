/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkoremote.v1_0;

import static io.opentelemetry.javaagent.instrumentation.pekkoremote.v1_0.VirtualFields.INBOUND_ENVELOPE_PROPAGATED_CONTEXT;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.pekkoremote.v1_0.internal.OtelRemoteInstrument;
import io.opentelemetry.instrumentation.pekkoremote.v1_0.internal.StringTextMapGetter;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.bootstrap.executors.ExecutorAdviceHelper;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.pekko.remote.artery.InboundEnvelope;
import org.apache.pekko.remote.artery.RemoteInstrument;

public class PekkoRemoteArteryRemoteInstrumentsInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.pekko.remote.artery.RemoteInstruments");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("deserializeInstrument")
            .and(takesArgument(0, named("org.apache.pekko.remote.artery.RemoteInstrument")))
            .and(takesArgument(1, named("org.apache.pekko.remote.artery.InboundEnvelope")))
            .and(takesArgument(2, named("java.nio.ByteBuffer"))),
        getClass().getName() + "$DeserializeInstrumentAdvice");
  }

  public static class DeserializeInstrumentAdvice {

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void exit(
        @Advice.Argument(0) RemoteInstrument remoteInstrument,
        @Advice.Argument(1) InboundEnvelope inboundEnvelope,
        @Advice.Thrown Throwable throwable) {
      if (remoteInstrument instanceof OtelRemoteInstrument) {
        OtelRemoteInstrument otelRemoteInstrument = ((OtelRemoteInstrument) remoteInstrument);
        try {
          if (throwable == null) {
            String metadata = otelRemoteInstrument.getCurrentlyReadMetadata();
            if (metadata != null) {
              Context context =
                  W3CTraceContextPropagator.getInstance()
                      .extract(
                          Java8BytecodeBridge.currentContext(),
                          metadata,
                          StringTextMapGetter.INSTANCE);
              if (context != null
                  && ExecutorAdviceHelper.shouldPropagateContext(context, InboundEnvelope.class)) {
                ExecutorAdviceHelper.attachContextToTask(
                    context, INBOUND_ENVELOPE_PROPAGATED_CONTEXT, inboundEnvelope);
              }
            }
          }
        } finally {
          otelRemoteInstrument.clearCurrentlyReadMetadata();
        }
      }
    }
  }
}
