/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.thrift.v0_9_1.server;

import static io.opentelemetry.javaagent.instrumentation.thrift.v0_9_1.ThriftSingletons.serverInstrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.internal.InstrumenterUtil;
import io.opentelemetry.instrumentation.api.internal.Timer;
import io.opentelemetry.instrumentation.thrift.common.RequestScopeContext;
import io.opentelemetry.instrumentation.thrift.common.SocketAccessor;
import io.opentelemetry.instrumentation.thrift.common.ThriftRequest;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.net.Socket;
import java.util.HashMap;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.thrift.protocol.TProtocol;

public final class ThriftMutiplexedProcessorInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.thrift.TMultiplexedProcessor");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod().and(named("process")),
        ThriftMutiplexedProcessorInstrumentation.class.getName() + "$ProcessAdvice");
  }

  public static class ProcessAdvice {

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Argument(value = 0) TProtocol inProtocol, @Advice.Thrown Throwable throwable) {
      if (inProtocol instanceof ServerInProtocolWrapper) {
        ServerInProtocolWrapper wrapper = (ServerInProtocolWrapper) inProtocol;
        String methodName = wrapper.getMethodName();
        if (methodName == null || methodName.isEmpty()) {
          return;
        }

        RequestScopeContext requestScopeContext = wrapper.getRequestScopeContext();
        if (requestScopeContext == null) {
          if (throwable != null) {
            Timer timer = wrapper.getTimer();
            Socket socket = SocketAccessor.getSocket(wrapper.getTransport());
            ThriftRequest request =
                ThriftRequest.create(
                    wrapper.getServiceName(), wrapper.getMethodName(), socket, new HashMap<>());
            Context parentContext = Java8BytecodeBridge.currentContext();
            if (serverInstrumenter().shouldStart(parentContext, request)) {
              InstrumenterUtil.startAndEnd(
                  serverInstrumenter(),
                  parentContext,
                  request,
                  null,
                  throwable,
                  timer.startTime(),
                  timer.now());
              wrapper.setRequestScopeContext(null);
            }
          }
          return;
        }

        requestScopeContext.close();
        Context context = requestScopeContext.getContext();
        serverInstrumenter().end(context, requestScopeContext.getRequest(), null, throwable);
      }
    }
  }
}
