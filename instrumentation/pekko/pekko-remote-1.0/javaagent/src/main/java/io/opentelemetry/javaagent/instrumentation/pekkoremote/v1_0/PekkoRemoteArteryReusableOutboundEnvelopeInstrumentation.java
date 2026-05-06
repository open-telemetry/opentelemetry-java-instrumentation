/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkoremote.v1_0;

import static io.opentelemetry.javaagent.instrumentation.pekkoremote.v1_0.VirtualFields.OUTBOUND_ENVELOPE_PROPAGATED_CONTEXT;
import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.bootstrap.executors.ExecutorAdviceHelper;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.pekko.remote.artery.ArteryMessage;
import org.apache.pekko.remote.artery.OutboundEnvelope;

public class PekkoRemoteArteryReusableOutboundEnvelopeInstrumentation
    implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.pekko.remote.artery.ReusableOutboundEnvelope");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(named("init"), getClass().getName() + "$InitAdvice");
  }

  public static class InitAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void exit(@Advice.Return OutboundEnvelope outboundEnvelope) {
      if (!(outboundEnvelope.message() instanceof ArteryMessage)) {
        Context context = Java8BytecodeBridge.currentContext();
        if (context != null
            && ExecutorAdviceHelper.shouldPropagateContext(context, outboundEnvelope)) {
          ExecutorAdviceHelper.attachContextToTask(
              context, OUTBOUND_ENVELOPE_PROPAGATED_CONTEXT, outboundEnvelope);
        }
      }
    }
  }
}
