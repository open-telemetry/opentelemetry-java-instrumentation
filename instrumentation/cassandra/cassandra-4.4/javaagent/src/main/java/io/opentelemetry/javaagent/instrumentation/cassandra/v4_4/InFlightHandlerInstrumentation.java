/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v4_4;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.datastax.oss.protocol.internal.Frame;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.net.InetSocketAddress;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class InFlightHandlerInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.datastax.oss.driver.internal.core.channel.InFlightHandler");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("channelRead").and(takesArguments(2)), getClass().getName() + "$ChannelReadAdvice");
  }

  @SuppressWarnings("unused")
  public static class ChannelReadAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static void onEnter(
        @Advice.Argument(0) Object context, @Advice.Argument(1) Object message) {
      if (!(message instanceof Frame)) {
        return;
      }
      InetSocketAddress remoteAddress = CassandraChannel.getRemoteAddress(context);
      if (remoteAddress != null) {
        VirtualFieldHelper.FRAME_PEER.set((Frame) message, remoteAddress);
      }
    }
  }
}
