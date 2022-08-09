/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v3_8;

import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.netty.common.NettyErrorHolder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class DefaultChannelPipelineInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.jboss.netty.channel.DefaultChannelPipeline");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("notifyHandlerException"))
            .and(takesArgument(1, named(Throwable.class.getName()))),
        DefaultChannelPipelineInstrumentation.class.getName() + "$NotifyHandlerExceptionAdvice");
  }

  @SuppressWarnings("unused")
  public static class NotifyHandlerExceptionAdvice {

    @Advice.OnMethodEnter
    public static void onEnter(@Advice.Argument(1) Throwable throwable) {
      if (throwable != null) {
        NettyErrorHolder.set(Java8BytecodeBridge.currentContext(), throwable);
      }
    }
  }
}
