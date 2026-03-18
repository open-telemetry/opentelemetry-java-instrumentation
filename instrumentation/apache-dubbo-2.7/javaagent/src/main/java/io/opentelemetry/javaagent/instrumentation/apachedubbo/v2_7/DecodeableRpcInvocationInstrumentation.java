/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachedubbo.v2_7;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.dubbo.rpc.RpcInvocation;

/**
 * Instruments {@code DecodeableRpcInvocation.decode(Channel, InputStream)} to capture unknown
 * service spans that fail during the decode phase.
 *
 * <p>In Dubbo 3.x, when a typed (non-generic) invocation arrives for an unknown service, the decode
 * fails with "Service not found" before {@code DubboProtocol.getInvoker()} is ever called. This
 * instrumentation complements {@link DubboProtocolInstrumentation} which handles the Dubbo 2.7 case
 * where decode succeeds but getInvoker() throws.
 */
public class DecodeableRpcInvocationInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.dubbo.rpc.protocol.dubbo.DecodeableRpcInvocation");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("decode")
            .and(takesArgument(0, named("org.apache.dubbo.remoting.Channel")))
            .and(takesArgument(1, named("java.io.InputStream"))),
        DecodeableRpcInvocationInstrumentation.class.getName() + "$DecodeAdvice");
  }

  @SuppressWarnings("unused")
  public static class DecodeAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static long onEnter() {
      if (DubboSingletons.SERVER_INSTRUMENTER == null) {
        return 0;
      }
      return System.currentTimeMillis();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.This RpcInvocation invocation,
        @Advice.Argument(0) Object channelObj,
        @Advice.Thrown Throwable throwable,
        @Advice.Enter long startTimeMillis) {
      if (throwable == null || startTimeMillis == 0) {
        return;
      }

      DubboUnknownServiceHelper.createUnknownServiceSpanFromDecode(
          invocation, channelObj, throwable, startTimeMillis);
    }
  }
}
