/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkoremote.v1_0.classic;

import static io.opentelemetry.javaagent.instrumentation.pekkoremote.v1_0.classic.VirtualFields.SEND_CONTEXT;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.pekko.remote.EndpointManager;

/**
 * Captures the context of the thread that sends a message to a remote actor. Remoting hands the
 * message to an endpoint actor that serializes it later, possibly from a buffer, where the context
 * of the sender is not available any more.
 */
class SendInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.pekko.remote.EndpointManager$Send");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(isConstructor(), getClass().getName() + "$ConstructorAdvice");
    transformer.applyAdviceToMethod(
        named("copy").and(takesArguments(4)), getClass().getName() + "$CopyAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConstructorAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.This EndpointManager.Send send) {
      Context context = Java8BytecodeBridge.currentContext();
      if (context != Java8BytecodeBridge.rootContext()) {
        SEND_CONTEXT.set(send, context);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class CopyAdvice {

    // remoting copies a message to add a sequence number to it, which happens on the endpoint
    // actor, the context of the sender is only on the message that is copied
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This EndpointManager.Send send, @Advice.Return EndpointManager.Send copy) {
      SEND_CONTEXT.set(copy, SEND_CONTEXT.get(send));
    }
  }
}
