/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachedubbo.v2_7;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.instrumentation.api.internal.Timer;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.time.Instant;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.RpcInvocation;

public class DubboProtocolInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.dubbo.rpc.protocol.dubbo.DubboProtocol");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("getInvoker")
            .and(takesArgument(0, named("org.apache.dubbo.remoting.Channel")))
            .and(takesArgument(1, named("org.apache.dubbo.rpc.Invocation"))),
        getClass().getName() + "$GetInvokerAdvice");
  }

  @SuppressWarnings("unused")
  public static class GetInvokerAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    @Nullable
    public static Timer onEnter() {
      if (!DubboUnknownServiceHelper.isEnabled()) {
        return null;
      }
      return Timer.start();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.Argument(0) Object channelObj,
        @Advice.Argument(1) Invocation inv,
        @Advice.Thrown Throwable throwable,
        @Advice.Enter @Nullable Timer timer) {
      if (throwable == null || timer == null || !(inv instanceof RpcInvocation)) {
        return;
      }

      Instant startTime = timer.startTime();
      Instant endTime = timer.now();
      DubboUnknownServiceHelper.createUnknownServiceSpan(
          (RpcInvocation) inv, channelObj, throwable, startTime, endTime);
    }
  }
}
