/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachedubbo.v2_7;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.instrumentation.apachedubbo.v2_7.internal.DubboStatusCodeHolder;
import io.opentelemetry.instrumentation.apachedubbo.v2_7.internal.DubboStatusCodeUtil;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.dubbo.remoting.exchange.Request;
import org.apache.dubbo.remoting.exchange.Response;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.RpcInvocation;

/**
 * Instruments {@code DefaultFuture.doReceived(Response)} to capture the Dubbo2 protocol response
 * status code and store it in a VirtualField on the RpcInvocation, making it available to the
 * library-level attributes extractors.
 */
public class DefaultFutureInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.dubbo.remoting.exchange.support.DefaultFuture");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("doReceived").and(takesArguments(1)),
        DefaultFutureInstrumentation.class.getName() + "$DoReceivedAdvice");
  }

  @SuppressWarnings("unused")
  public static class DoReceivedAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(0) Response response,
        @Advice.FieldValue("request") Request request) {

      if (response == null || request == null) {
        return;
      }

      byte status = response.getStatus();
      String statusCodeName = DubboStatusCodeUtil.dubbo2StatusCodeToString(status);
      boolean isServerError = DubboStatusCodeUtil.isDubbo2ServerError(statusCodeName);

      Object data = request.getData();
      if (data instanceof Invocation) {
        VirtualField<RpcInvocation, DubboStatusCodeHolder> field =
            VirtualField.find(RpcInvocation.class, DubboStatusCodeHolder.class);
        field.set((RpcInvocation) data, new DubboStatusCodeHolder(statusCodeName, isServerError));
      }
    }
  }
}
