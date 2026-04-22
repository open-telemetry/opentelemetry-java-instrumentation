/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.thrift.v0_9_1.client;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.extendsClass;
import static io.opentelemetry.javaagent.instrumentation.thrift.v0_9_1.ThriftSingletons.clientInstrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.isProtected;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.thrift.common.RequestScopeContext;
import io.opentelemetry.instrumentation.thrift.common.client.MethodAccessor;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.Set;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.thrift.protocol.TProtocol;

class ThriftClientInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return extendsClass(named("org.apache.thrift.TServiceClient"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isConstructor().and(takesArguments(1)), getClass().getName() + "$ConstructorOneAdvice");

    transformer.applyAdviceToMethod(
        isConstructor().and(takesArguments(2)), getClass().getName() + "$ConstructorTwoAdvice");

    transformer.applyAdviceToMethod(
        isProtected().and(nameStartsWith("sendBase")), getClass().getName() + "$ClientSendAdvice");

    transformer.applyAdviceToMethod(
        named("receiveBase"), getClass().getName() + "$ClientReceiveAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConstructorOneAdvice {
    @AssignReturned.ToArguments(@ToArgument(0))
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static TProtocol onEnter(
        @Advice.Origin("#t") String serviceName, @Advice.Argument(0) TProtocol inProtocol) {
      if (inProtocol instanceof ClientOutProtocolWrapper) {
        return inProtocol;
      }
      Set<String> voidMethodNames = MethodAccessor.voidMethodNames(serviceName);
      return new ClientOutProtocolWrapper(inProtocol, serviceName, voidMethodNames);
    }
  }

  @SuppressWarnings("unused")
  public static class ConstructorTwoAdvice {
    @AssignReturned.ToArguments({
      @ToArgument(value = 0, index = 0),
      @ToArgument(value = 1, index = 1)
    })
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Object[] onEnter(
        @Advice.Origin("#t") String serviceName,
        @Advice.Argument(0) TProtocol inProtocol,
        @Advice.Argument(1) TProtocol outProtocol) {
      Set<String> voidMethodNames = MethodAccessor.voidMethodNames(serviceName);
      TProtocol inProtocolResult = inProtocol;
      TProtocol outProtocolResult = outProtocol;
      if (!(inProtocol instanceof ClientOutProtocolWrapper)) {
        inProtocolResult = new ClientOutProtocolWrapper(inProtocol, serviceName, voidMethodNames);
      }
      if (!(outProtocol instanceof ClientOutProtocolWrapper)) {
        outProtocolResult = new ClientOutProtocolWrapper(outProtocol, serviceName, voidMethodNames);
      }
      return new Object[] {inProtocolResult, outProtocolResult};
    }
  }

  @SuppressWarnings("unused")
  public static class ClientSendAdvice {
    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void methodExit(
        @Advice.FieldValue("oprot_") TProtocol outProtocol, @Advice.Thrown Throwable throwable) {
      if (outProtocol instanceof ClientOutProtocolWrapper) {
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

  @SuppressWarnings("unused")
  public static class ClientReceiveAdvice {
    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void methodExit(
        @Advice.Thrown Throwable throwable, @Advice.FieldValue("oprot_") TProtocol outProtocol) {
      if (outProtocol instanceof ClientOutProtocolWrapper) {
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
