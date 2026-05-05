/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.finaglehttp.v23_11;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Bridges the instrumented netty instrumentation to finagle's http/2 netty integrations. Without
 * this the link is broken as the netty ServerContexts don't pass through to the last handler in the
 * pipeline where it's needed.
 */
class H2StreamChannelInitInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    // scala object instance -- append $ to name
    return named("com.twitter.finagle.http2.transport.common.H2StreamChannelInit$");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("initServer").and(returns(named("io.netty.channel.ChannelInitializer"))),
        getClass().getName() + "$InitServerAdvice");
    transformer.applyAdviceToMethod(
        named("initClient").and(returns(named("io.netty.channel.ChannelInitializer"))),
        getClass().getName() + "$InitClientAdvice");
  }

  @SuppressWarnings("unused")
  public static class InitServerAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    @Advice.AssignReturned.ToReturned
    public static ChannelInitializer<Channel> handleExit(
        @Advice.Return ChannelInitializer<Channel> initializer) {
      return Helpers.wrapServer(initializer);
    }
  }

  @SuppressWarnings("unused")
  public static class InitClientAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    @Advice.AssignReturned.ToReturned
    public static ChannelInitializer<Channel> handleExit(
        @Advice.Return ChannelInitializer<Channel> initializer) {
      return Helpers.wrapClient(initializer);
    }
  }
}
