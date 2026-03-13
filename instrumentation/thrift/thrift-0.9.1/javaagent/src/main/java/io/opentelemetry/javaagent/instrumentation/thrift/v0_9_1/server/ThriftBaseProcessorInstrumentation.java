/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.thrift.v0_9_1.server;

import static io.opentelemetry.javaagent.instrumentation.thrift.v0_9_1.ThriftSingletons.serverInstrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.thrift.common.RequestScopeContext;
import io.opentelemetry.instrumentation.thrift.common.client.MethodAccessor;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.lang.reflect.Field;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolDecorator;

public final class ThriftBaseProcessorInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.thrift.TBaseProcessor");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod().and(named("process")),
        ThriftBaseProcessorInstrumentation.class.getName() + "$ProcessAdvice");
  }

  public static class ProcessAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(
        @Advice.Argument(value = 0) TProtocol inProtocol,
        @Advice.FieldValue(value = "iface") Object iface)
        throws IllegalAccessException {
      String serviceName = iface.getClass().getName();
      if (inProtocol instanceof ServerInProtocolWrapper) {
        ServerInProtocolWrapper wrapper = (ServerInProtocolWrapper) inProtocol;
        wrapper.setServiceName(serviceName);
      } else if (inProtocol instanceof TProtocolDecorator) {
        // TMultiplexedProcessor compatible
        Field field = MethodAccessor.getConcreteProtocolField(TProtocolDecorator.class);
        Object obj = field.get(inProtocol);
        if (obj != null && obj instanceof ServerInProtocolWrapper) {
          ServerInProtocolWrapper wrapper = (ServerInProtocolWrapper) obj;
          wrapper.setServiceName(serviceName);
        }
      }
    }

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
