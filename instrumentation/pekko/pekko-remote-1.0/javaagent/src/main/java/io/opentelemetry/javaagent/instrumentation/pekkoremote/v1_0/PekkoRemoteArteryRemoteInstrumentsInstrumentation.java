/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkoremote.v1_0;

import static io.opentelemetry.javaagent.instrumentation.pekkoremote.v1_0.VirtualFields.BYTE_BUFFER_OTEL_METADATA;
import static io.opentelemetry.javaagent.instrumentation.pekkoremote.v1_0.VirtualFields.INBOUND_ENVELOPE_PROPAGATED_CONTEXT;
import static java.util.Arrays.asList;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.bootstrap.executors.ExecutorAdviceHelper;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import javax.annotation.Nullable;
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

    public enum StringTextMapGetter implements TextMapGetter<String> {
      INSTANCE;

      @Override
      public Iterable<String> keys(String carrier) {
        String[] keys = carrier.split("=[^,]*,?");
        return asList(keys);
      }

      @Nullable
      @Override
      public String get(@Nullable String carrier, String key) {
        if (carrier != null) {
          String[] keyValues = carrier.split(",");
          for (int i = 0; i < keyValues.length; i++) {
            String[] keyValue = keyValues[i].split("=");
            if (keyValue.length == 2 && keyValue[0].equals(key)) {
              try {
                return URLDecoder.decode(keyValue[1], "UTF-8");
              } catch (UnsupportedEncodingException e) {
                // ignore
              }
            }
          }
        }
        return null;
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void exit(
        @Advice.Argument(0) RemoteInstrument remoteInstrument,
        @Advice.Argument(1) InboundEnvelope inboundEnvelope,
        @Advice.Argument(2) ByteBuffer byteBuffer,
        @Advice.Thrown Throwable throwable) {
      if (remoteInstrument
          instanceof
          PekkoRemoteArteryRemoteInstrumentsCompanionInstrumentation.CreateAdvice
              .OtelRemoteInstrument) {
        try {
          if (throwable == null) {
            PekkoRemoteArteryRemoteInstrumentsCompanionInstrumentation.CreateAdvice
                    .PekkoRemoteMetadata
                metadata = BYTE_BUFFER_OTEL_METADATA.get(byteBuffer);
            if (metadata != null) {
              Context context =
                  W3CTraceContextPropagator.getInstance()
                      .extract(
                          Java8BytecodeBridge.currentContext(),
                          metadata.getMetadata(),
                          StringTextMapGetter.INSTANCE);
              if (context != null
                  && ExecutorAdviceHelper.shouldPropagateContext(context, InboundEnvelope.class)) {
                ExecutorAdviceHelper.attachContextToTask(
                    context, INBOUND_ENVELOPE_PROPAGATED_CONTEXT, inboundEnvelope);
              }
            }
          }
        } finally {
          BYTE_BUFFER_OTEL_METADATA.set(byteBuffer, null);
        }
      }
    }
  }
}
