/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkoremote.v1_0;

import static io.opentelemetry.javaagent.instrumentation.pekkoremote.v1_0.VirtualFields.OUTBOUND_ENVELOPE_PROPAGATED_CONTEXT;
import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.bootstrap.executors.TaskAdviceHelper;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.pekko.remote.artery.ArteryMessage;
import org.apache.pekko.remote.artery.OutboundEnvelope;

public class PekkoRemoteMessageSerializerInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.pekko.remote.MessageSerializer$");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("serializeForArtery"), getClass().getName() + "$SerializeForArteryAdvice");
  }

  public static class SerializeForArteryAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Scope enter(@Advice.Argument(1) OutboundEnvelope outboundEnvelope) {
      if (!(outboundEnvelope.message() instanceof ArteryMessage)) {
        if (OUTBOUND_ENVELOPE_PROPAGATED_CONTEXT.get(outboundEnvelope) != null) {

          return TaskAdviceHelper.makePropagatedContextCurrent(
              OUTBOUND_ENVELOPE_PROPAGATED_CONTEXT, outboundEnvelope);

        } else {
          return null;
        }
      } else {
        return null;
      }
    }

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void exit(@Advice.Enter Scope scope) {
      if (scope != null) {
        scope.close();
      }
    }
  }
}
