/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v4_0;

import static io.opentelemetry.javaagent.instrumentation.lettuce.v4_0.LettuceSingletons.CONNECTION_ADDRESS;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.lambdaworks.redis.ConnectionBuilder;
import com.lambdaworks.redis.RedisChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class LettuceConnectionInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return namedOneOf(
        "com.lambdaworks.redis.ConnectionBuilder", "com.lambdaworks.redis.protocol.CommandHandler");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("build").and(takesArguments(0)), getClass().getName() + "$BuildAdvice");
    transformer.applyAdviceToMethod(
        named("channelActive"), getClass().getName() + "$ChannelActiveAdvice");
  }

  @SuppressWarnings("unused")
  public static class BuildAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static void onEnter(@Advice.This ConnectionBuilder builder) {
      RedisChannelHandler<?, ?> connection = builder.connection();
      SocketAddress address = builder.socketAddress();
      if (connection != null && address instanceof InetSocketAddress) {
        CONNECTION_ADDRESS.set(connection, (InetSocketAddress) address);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class ChannelActiveAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static void onEnter(
        @Advice.Argument(0) ChannelHandlerContext context,
        @Advice.FieldValue("redisChannelHandler") @Nullable RedisChannelHandler<?, ?> connection) {
      SocketAddress address = context.channel().remoteAddress();
      if (connection != null && address instanceof InetSocketAddress) {
        CONNECTION_ADDRESS.set(connection, (InetSocketAddress) address);
      }
    }
  }
}
