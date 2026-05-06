/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkoremote.v1_0;

import static io.opentelemetry.javaagent.instrumentation.pekkoremote.v1_0.VirtualFields.INBOUND_ENVELOPE_PROPAGATED_CONTEXT;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.bootstrap.executors.TaskAdviceHelper;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.pekko.remote.artery.InboundEnvelope;

public class PekkoRemoteArteryMessageDispatcherInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.pekko.remote.artery.MessageDispatcher");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("dispatch")
            .and(takesArgument(0, named("org.apache.pekko.remote.artery.InboundEnvelope"))),
        getClass().getName() + "$DispatchAdvice");
  }

  public static class DispatchAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Scope enter(@Advice.Argument(0) InboundEnvelope inboundEnvelope) {
      return TaskAdviceHelper.makePropagatedContextCurrent(
          INBOUND_ENVELOPE_PROPAGATED_CONTEXT, inboundEnvelope);
    }

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void exit(@Advice.Enter Scope scope) {
      if (scope != null) {
        scope.close();
      }
    }
  }
}
