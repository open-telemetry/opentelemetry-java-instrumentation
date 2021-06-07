/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4_0;

import static io.opentelemetry.javaagent.instrumentation.netty.v4_0.server.NettyHttpServerTracer.tracer;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class AbstractChannelHandlerContextInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    // Different classes depending on Netty version
    return namedOneOf(
        "io.netty.channel.AbstractChannelHandlerContext",
        "io.netty.channel.DefaultChannelHandlerContext");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("notifyHandlerException"))
            .and(takesArgument(0, named(Throwable.class.getName()))),
        AbstractChannelHandlerContextInstrumentation.class.getName()
            + "$NotifyHandlerExceptionAdvice");
  }

  public static class NotifyHandlerExceptionAdvice {
    @Advice.OnMethodEnter
    public static void onEnter(@Advice.Argument(0) Throwable throwable) {
      if (throwable != null) {
        tracer().onException(Java8BytecodeBridge.currentContext(), throwable);
      }
    }
  }
}
