/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.finagle;

import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;

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
        isMethod().and(named("initServer")),
        H2StreamChannelInitInstrumentation.class.getName() + "$InitServerHandler");
    transformer.applyAdviceToMethod(
        isMethod().and(named("initClient")),
        H2StreamChannelInitInstrumentation.class.getName() + "$InitClientHandler");
  }

  @SuppressWarnings({"unused", "OtelPrivateConstructorForUtilityClass"})
  public static class InitServerHandler {

    @Advice.OnMethodExit
    public static void handleExit(
        @Advice.Return(readOnly = false) ChannelInitializer<Channel> initializer) {
      initializer = Helpers.wrapServer(initializer);
    }
  }

  @SuppressWarnings({"unused", "OtelPrivateConstructorForUtilityClass"})
  public static class InitClientHandler {

    @Advice.OnMethodExit
    public static void handleExit(
        @Advice.Return(readOnly = false) ChannelInitializer<Channel> initializer) {
      initializer = Helpers.wrapClient(initializer);
    }
  }
}
