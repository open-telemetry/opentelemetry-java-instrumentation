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
import java.io.InputStream;
import java.time.Instant;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.dubbo.rpc.RpcInvocation;

/**
 * Instruments {@code DecodeableRpcInvocation.decode(Channel, InputStream)} to capture unknown
 * service spans for the Dubbo protocol (binary) that fail during the decode phase.
 *
 * <p>When {@code PermittedSerializationKeeper} is enforced (newer Dubbo versions), typed
 * invocations to unknown services fail during decode before {@code DubboProtocol.getInvoker()} is
 * ever called. This instrumentation complements {@link DubboProtocolInstrumentation} which handles
 * the case where decode succeeds but getInvoker() throws.
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
            .and(takesArgument(1, InputStream.class)),
        getClass().getName() + "$DecodeAdvice");
  }

  @SuppressWarnings("unused")
  public static class DecodeAdvice {

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
        @Advice.This RpcInvocation invocation,
        @Advice.Argument(0) Object channelObj,
        @Advice.Thrown Throwable throwable,
        @Advice.Enter @Nullable Timer timer) {
      if (throwable == null || timer == null) {
        return;
      }

      Instant startTime = timer.startTime();
      Instant endTime = timer.now();
      DubboUnknownServiceHelper.createUnknownServiceSpanFromDecode(
          invocation, channelObj, throwable, startTime, endTime);
    }
  }
}
