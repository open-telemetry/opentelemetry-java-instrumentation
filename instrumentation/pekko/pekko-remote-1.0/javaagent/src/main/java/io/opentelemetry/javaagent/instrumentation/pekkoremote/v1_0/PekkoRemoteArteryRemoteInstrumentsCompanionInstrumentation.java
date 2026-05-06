/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkoremote.v1_0;

import static io.opentelemetry.javaagent.instrumentation.pekkoremote.v1_0.VirtualFields.BYTE_BUFFER_OTEL_METADATA;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.util.Arrays.asList;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSelectionMessage;
import org.apache.pekko.remote.HeartbeatMessage;
import org.apache.pekko.remote.artery.ArteryMessage;
import org.apache.pekko.remote.artery.RemoteInstrument;
import org.apache.pekko.util.Unsafe;
import scala.collection.JavaConverters;
import scala.collection.immutable.Vector;

public class PekkoRemoteArteryRemoteInstrumentsCompanionInstrumentation
    implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.pekko.remote.artery.RemoteInstruments$");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("create")
            .and(takesArgument(0, named("org.apache.pekko.actor.ExtendedActorSystem")))
            .and(takesArgument(1, named("org.apache.pekko.event.LoggingAdapter"))),
        getClass().getName() + "$CreateAdvice");
  }

  public static class CreateAdvice {

    public static class PekkoRemoteMetadata {

      private final String metadata;

      public PekkoRemoteMetadata(String metadata) {
        this.metadata = metadata;
      }

      public String getMetadata() {
        return metadata;
      }
    }

    public static class OtelRemoteInstrument extends RemoteInstrument {

      private enum StringBuilderTextMapSetter implements TextMapSetter<StringBuilder> {
        INSTANCE;

        @Override
        public void set(@Nullable StringBuilder carrier, String key, String value) {
          if (carrier.length() > 0) {
            carrier.append(',');
          }
          carrier.append(key);
          carrier.append('=');
          try {
            carrier.append(URLEncoder.encode(value, "UTF-8"));
          } catch (UnsupportedEncodingException e) {
            // ignore
          }
        }
      }

      @Override
      public byte identifier() {
        return 8;
      }

      @Override
      public void remoteWriteMetadata(
          ActorRef recipient, Object message, ActorRef sender, ByteBuffer buffer) {
        if (nonPekkoInternalMessage(message)) {

          StringBuilder carrier = new StringBuilder();
          W3CTraceContextPropagator.getInstance()
              .inject(Context.current(), carrier, StringBuilderTextMapSetter.INSTANCE);

          byte[] bytes = new byte[carrier.length()];
          Unsafe.copyUSAsciiStrToBytes(carrier.toString(), bytes);

          buffer.putShort((short) bytes.length);
          buffer.put(bytes);

        } else {
          buffer.putShort((short) 0);
        }
      }

      @Override
      public void remoteMessageSent(
          ActorRef recipient, Object message, ActorRef sender, int size, long time) {}

      @Override
      public void remoteReadMetadata(
          ActorRef recipient, Object message, ActorRef sender, ByteBuffer buffer) {
        int length = buffer.getShort();

        if (length > 0) {

          byte[] bytes = new byte[length];
          buffer.get(bytes);

          String metadata = new String(bytes, US_ASCII);

          BYTE_BUFFER_OTEL_METADATA.set(buffer, new PekkoRemoteMetadata(metadata));
        } else {
          BYTE_BUFFER_OTEL_METADATA.set(buffer, null);
        }
      }

      @Override
      public void remoteMessageReceived(
          ActorRef recipient, Object message, ActorRef sender, int size, long time) {}

      private static boolean nonPekkoInternalMessage(Object message) {
        if (message instanceof ActorSelectionMessage) {
          message = ((ActorSelectionMessage) message).message();
        }
        return !(message instanceof HeartbeatMessage) && !(message instanceof ArteryMessage);
      }
    }

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    @Advice.AssignReturned.ToReturned
    public static Vector<RemoteInstrument> exit(
        @Advice.Return Vector<RemoteInstrument> remoteInstruments) {
      RemoteInstrument[] updatedRemoteInstruments =
          new RemoteInstrument[remoteInstruments.length() + 1];
      for (int i = 0; i < remoteInstruments.length(); i++) {
        updatedRemoteInstruments[0] = remoteInstruments.apply(i);
      }
      updatedRemoteInstruments[remoteInstruments.length()] = new OtelRemoteInstrument();
      return JavaConverters.asScalaBuffer(asList(updatedRemoteInstruments)).toVector();
    }
  }
}
