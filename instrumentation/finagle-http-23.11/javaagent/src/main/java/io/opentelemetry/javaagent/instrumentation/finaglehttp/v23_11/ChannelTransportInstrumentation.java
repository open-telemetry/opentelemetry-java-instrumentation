/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.finaglehttp.v23_11;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;

import io.netty.channel.Channel;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/** Amends the tail of the Netty pipeline to bridge the netty request to its finagle request. */
class ChannelTransportInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.twitter.finagle.netty4.transport.ChannelTransport");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isConstructor(), ChannelTransportInstrumentation.class.getName() + "$ConstructorAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConstructorAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void methodExit(@Advice.Argument(0) Channel ch) {
      //
      // TODO add extra outbound handler to the end of the ChannelTransport Channel
      //  (or create a duplex one with the existing read-side handler at the end of the pipeline);
      //  this will allow us to hook the Context attached to the netty HttpRequest
      // (Bijections$finagle$)
      //  to the netty pipeline (need to look up how the netty client side handles things)
      //
      Helpers.mutateHandlerPipeline(ch);
    }
  }
}
