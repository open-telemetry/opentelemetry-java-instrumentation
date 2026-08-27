/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkoremote.v1_0.classic;

import static io.opentelemetry.javaagent.instrumentation.pekkoremote.v1_0.classic.VirtualFields.SERIALIZED_MESSAGE_CONTEXT;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.pekkoremote.v1_0.ProtobufContext;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.pekko.remote.transport.PekkoPduCodec;
import org.apache.pekko.util.ByteString;
import scala.Tuple2;

/** Reads and writes the context that classic remoting messages carry. */
class PduCodecInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.pekko.remote.transport.PekkoPduProtobufCodec$");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("constructMessage"), getClass().getName() + "$ConstructMessageAdvice");
    transformer.applyAdviceToMethod(
        named("decodeMessage").and(takesArgument(0, named("org.apache.pekko.util.ByteString"))),
        getClass().getName() + "$DecodeMessageAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConstructMessageAdvice {

    @Advice.AssignReturned.ToReturned
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static ByteString onExit(@Advice.Return ByteString pdu) {
      Context context = Java8BytecodeBridge.currentContext();
      if (context == Java8BytecodeBridge.rootContext()) {
        return pdu;
      }
      byte[] field = ProtobufContext.encode(context);
      if (field.length == 0) {
        return pdu;
      }

      byte[] message = pdu.toArrayUnsafe();
      // a message that no longer fits is discarded rather than truncated, so it is sent without a
      // context rather than not sent at all
      if (!ClassicPayloadLimit.fits(message.length + field.length)) {
        return pdu;
      }
      byte[] result = new byte[message.length + field.length];
      System.arraycopy(message, 0, result, 0, message.length);
      System.arraycopy(field, 0, result, message.length, field.length);
      return ByteString.fromArrayUnsafe(result);
    }
  }

  @SuppressWarnings("unused")
  public static class DecodeMessageAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Argument(0) ByteString raw, @Advice.Return Tuple2<?, ?> result) {
      PekkoPduCodec.Message message = MessageHelper.messageOf(result);
      if (message == null) {
        return;
      }
      Context context = ProtobufContext.decode(raw.toArrayUnsafe());
      if (context != null) {
        SERIALIZED_MESSAGE_CONTEXT.set(message.serializedMessage(), context);
      }
    }
  }
}
