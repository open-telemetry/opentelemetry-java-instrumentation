/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.finaglehttp.v23_11;

import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class H2StreamChannelInitInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    // scala object instance -- append $ to name
    return named("com.twitter.finagle.http2.transport.common.H2StreamChannelInit$");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("initServer"))
            .and(returns(named("io.netty.channel.ChannelInitializer"))),
        H2StreamChannelInitInstrumentation.class.getName() + "$InitServerAdvice");
    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("initClient"))
            .and(returns(named("io.netty.channel.ChannelInitializer"))),
        H2StreamChannelInitInstrumentation.class.getName() + "$InitClientAdvice");
  }

  @SuppressWarnings("unused")
  public static class InitServerAdvice {

    @Advice.OnMethodExit
    @Advice.AssignReturned.ToReturned
    public static ChannelInitializer<Channel> handleExit(
        @Advice.Return ChannelInitializer<Channel> initializer) {
      return Helpers.wrapServer(initializer);
    }
  }

  @SuppressWarnings("unused")
  public static class InitClientAdvice {

    @Advice.OnMethodExit
    @Advice.AssignReturned.ToReturned
    public static ChannelInitializer<Channel> handleExit(
        @Advice.Return ChannelInitializer<Channel> initializer) {
      return Helpers.wrapClient(initializer);
    }
  }
}
