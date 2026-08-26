/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkoremote.v1_0.classic;

import static io.opentelemetry.javaagent.instrumentation.pekkoremote.v1_0.classic.VirtualFields.SEND_CONTEXT;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.pekko.remote.EndpointManager;

/**
 * Makes the context of the sender current while the message is serialized, the codec writes the
 * context that is current when it builds the message.
 */
class EndpointWriterInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.pekko.remote.EndpointWriter");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("writeSend")
            .and(takesArgument(0, named("org.apache.pekko.remote.EndpointManager$Send"))),
        getClass().getName() + "$WriteSendAdvice");
  }

  @SuppressWarnings("unused")
  public static class WriteSendAdvice {

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Scope onEnter(@Advice.Argument(0) EndpointManager.Send send) {
      Context context = SEND_CONTEXT.get(send);
      return context == null ? null : context.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.Enter @Nullable Scope scope) {
      if (scope != null) {
        scope.close();
      }
    }
  }
}
