/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.thrift.v0_9_1.server;

import static io.opentelemetry.javaagent.instrumentation.thrift.v0_9_1.ThriftSingletons.serverInstrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.thrift.common.RequestScopeContext;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.server.AbstractNonblockingServer;

public final class ThriftBaseAsyncProcessorInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.thrift.TBaseAsyncProcessor");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod().and(named("process")).and(takesArguments(1)),
        ThriftBaseAsyncProcessorInstrumentation.class.getName() + "$ProcessAdvice");
  }

  public static class ProcessAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(
        @Advice.Argument(0) AbstractNonblockingServer.AsyncFrameBuffer fb,
        @Advice.FieldValue(value = "iface") Object iface) {
      String serviceName = iface.getClass().getName();
      TProtocol inProtocol = fb.getInputProtocol();
      if (inProtocol instanceof ServerInProtocolWrapper) {
        ServerInProtocolWrapper wrapper = (ServerInProtocolWrapper) inProtocol;
        wrapper.setServiceName(serviceName);
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Argument(0) AbstractNonblockingServer.AsyncFrameBuffer fb,
        @Advice.Thrown Throwable throwable) {
      TProtocol inProtocol = fb.getInputProtocol();
      if (inProtocol instanceof ServerInProtocolWrapper) {
        ServerInProtocolWrapper wrapper = (ServerInProtocolWrapper) inProtocol;
        if (throwable == null && !wrapper.isOneway()) {
          return;
        }

        RequestScopeContext requestScopeContext = wrapper.getRequestScopeContext();
        if (requestScopeContext == null) {
          return;
        }

        requestScopeContext.close();
        Context context = requestScopeContext.getContext();
        serverInstrumenter().end(context, requestScopeContext.getRequest(), null, throwable);
        wrapper.setRequestScopeContext(null);
      }
    }
  }
}
