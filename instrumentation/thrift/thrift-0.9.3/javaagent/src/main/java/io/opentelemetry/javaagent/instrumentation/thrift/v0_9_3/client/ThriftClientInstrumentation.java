/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.thrift.v0_9_3.client;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.extendsClass;
import static io.opentelemetry.javaagent.instrumentation.thrift.v0_9_1.ThriftSingletons.clientInstrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPrivate;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.thrift.common.RequestScopeContext;
import io.opentelemetry.instrumentation.thrift.common.client.MethodAccessor;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.thrift.v0_9_1.client.ClientOutProtocolWrapper;
import java.util.Set;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.thrift.protocol.TProtocol;

public final class ThriftClientInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return extendsClass(named("org.apache.thrift.TServiceClient"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isConstructor().and(takesArguments(1)),
        ThriftClientInstrumentation.class.getName() + "$ConstructorOneAdvice");

    transformer.applyAdviceToMethod(
        isConstructor().and(takesArguments(2)),
        ThriftClientInstrumentation.class.getName() + "$ConstructorTowAdvice");

    transformer.applyAdviceToMethod(
        isMethod().and(isPrivate()).and(named("sendBase")),
        ThriftClientInstrumentation.class.getName() + "$ClientSendAdvice");

    transformer.applyAdviceToMethod(
        isMethod().and(named("receiveBase")),
        ThriftClientInstrumentation.class.getName() + "$ClientReceiveAdvice");
  }

  public static class ConstructorOneAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Origin("#t") String serviceName,
        @Advice.Argument(value = 0, readOnly = false) TProtocol inProtocol) {
      Set<String> voidMethodNames = MethodAccessor.voidMethodNames(serviceName);
      if (!(inProtocol instanceof ClientOutProtocolWrapper)) {
        inProtocol = new ClientOutProtocolWrapper(inProtocol, serviceName, voidMethodNames);
      }
    }
  }

  public static class ConstructorTowAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Origin("#t") String serviceName,
        @Advice.Argument(value = 0, readOnly = false) TProtocol inProtocol,
        @Advice.Argument(value = 1, readOnly = false) TProtocol outProtocol) {
      Set<String> voidMethodNames = MethodAccessor.voidMethodNames(serviceName);
      if (!(inProtocol instanceof ClientOutProtocolWrapper)) {
        inProtocol = new ClientOutProtocolWrapper(inProtocol, serviceName, voidMethodNames);
      }
      if (!(outProtocol instanceof ClientOutProtocolWrapper)) {
        outProtocol = new ClientOutProtocolWrapper(outProtocol, serviceName, voidMethodNames);
      }
    }
  }

  public static class ClientSendAdvice {
    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.FieldValue(value = "oprot_") TProtocol outProtocol,
        @Advice.Thrown Throwable throwable) {
      if (outProtocol != null && outProtocol instanceof ClientOutProtocolWrapper) {
        ClientOutProtocolWrapper wrapper = (ClientOutProtocolWrapper) outProtocol;
        RequestScopeContext requestScopeContext = wrapper.getRequestScopeContext();
        if (requestScopeContext == null) {
          return;
        }

        Context context = requestScopeContext.getContext();
        if (throwable != null) {
          requestScopeContext.close();
          clientInstrumenter().end(context, requestScopeContext.getRequest(), null, throwable);
          wrapper.setRequestScopeContext(null);
          return;
        }

        if (wrapper.isOneway()) {
          requestScopeContext.close();
          clientInstrumenter().end(context, requestScopeContext.getRequest(), 0, null);
          wrapper.setRequestScopeContext(null);
        }
      }
    }
  }

  public static class ClientReceiveAdvice {
    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Thrown Throwable throwable,
        @Advice.FieldValue(value = "oprot_") TProtocol outProtocol) {
      if (outProtocol != null && outProtocol instanceof ClientOutProtocolWrapper) {
        ClientOutProtocolWrapper wrapper = (ClientOutProtocolWrapper) outProtocol;
        RequestScopeContext requestScopeContext = wrapper.getRequestScopeContext();
        if (requestScopeContext == null) {
          return;
        }
        requestScopeContext.close();
        Context context = requestScopeContext.getContext();
        clientInstrumenter().end(context, requestScopeContext.getRequest(), null, throwable);
        wrapper.setRequestScopeContext(null);
      }
    }
  }
}
