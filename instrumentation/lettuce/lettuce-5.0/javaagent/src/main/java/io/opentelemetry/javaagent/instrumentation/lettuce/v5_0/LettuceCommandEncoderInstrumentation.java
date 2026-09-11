/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.lettuce.core.protocol.RedisCommand;
import io.netty.channel.ChannelHandlerContext;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.net.SocketAddress;
import java.util.Collection;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class LettuceCommandEncoderInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("io.lettuce.core.protocol.CommandEncoder");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("encode")
            .and(takesArguments(3))
            .and(takesArgument(0, named("io.netty.channel.ChannelHandlerContext")))
            .and(takesArgument(1, Object.class)),
        getClass().getName() + "$EncodeAdvice");
  }

  @SuppressWarnings("unused")
  public static class EncodeAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static void onEnter(
        @Advice.Argument(0) ChannelHandlerContext context, @Advice.Argument(1) Object message) {
      SocketAddress remoteAddress = context.channel().remoteAddress();
      if (remoteAddress == null) {
        return;
      }
      recordCommandPeers(message, remoteAddress);
    }

    public static void recordCommandPeers(Object message, SocketAddress remoteAddress) {
      if (message instanceof RedisCommand) {
        LettuceSingletons.recordCommandPeer((RedisCommand<?, ?, ?>) message, remoteAddress);
      } else if (message instanceof Collection) {
        for (Object item : (Collection<?>) message) {
          if (item instanceof RedisCommand) {
            LettuceSingletons.recordCommandPeer((RedisCommand<?, ?, ?>) item, remoteAddress);
          }
        }
      }
    }
  }
}
