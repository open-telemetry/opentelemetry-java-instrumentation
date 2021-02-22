/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4_0;

import static io.opentelemetry.javaagent.instrumentation.netty.v4_0.server.NettyHttpServerTracer.tracer;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class AbstractChannelHandlerContextInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    // Different classes depending on Netty version
    return named("io.netty.channel.AbstractChannelHandlerContext")
        .or(named("io.netty.channel.DefaultChannelHandlerContext"));
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod()
            .and(named("notifyHandlerException"))
            .and(takesArgument(0, named(Throwable.class.getName()))),
        AbstractChannelHandlerContextInstrumentation.class.getName()
            + "$NotifyHandlerExceptionAdvice");
  }

  public static class NotifyHandlerExceptionAdvice {
    @Advice.OnMethodEnter
    public static void onEnter(@Advice.Argument(0) Throwable throwable) {
      Span span = Java8BytecodeBridge.currentSpan();
      if (span.getSpanContext().isValid() && throwable != null) {
        tracer().addThrowable(span, throwable);
      }
    }
  }
}
