/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachedubbo.v2_7;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.net.InetSocketAddress;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.dubbo.remoting.Channel;
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
        DubboProtocolInstrumentation.class.getName() + "$GetInvokerAdvice");
  }

  @SuppressWarnings("unused")
  public static class GetInvokerAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static long onEnter() {
      if (DubboSingletons.SERVER_INSTRUMENTER == null) {
        return 0;
      }
      return System.currentTimeMillis();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.Argument(0) Object channelObj,
        @Advice.Argument(1) Invocation inv,
        @Advice.Thrown Throwable throwable,
        @Advice.Enter long startTimeMillis) {
      if (throwable == null || startTimeMillis == 0 || !(inv instanceof RpcInvocation)) {
        return;
      }

      InetSocketAddress remoteAddress = null;
      InetSocketAddress localAddress = null;
      try {
        Channel channel = (Channel) channelObj;
        remoteAddress = channel.getRemoteAddress();
        localAddress = channel.getLocalAddress();
      } catch (Throwable ignored) {
        // channel type may not match in some versions
      }

      DubboUnknownServiceHelper.createUnknownServiceSpan(
          (RpcInvocation) inv, remoteAddress, localAddress, throwable, startTimeMillis);
    }
  }
}
