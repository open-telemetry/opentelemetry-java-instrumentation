/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.thrift.v0_9_1.client;

import static io.opentelemetry.instrumentation.thrift.common.client.VirtualFields.ASYNC_METHOD_CALLBACK;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.extendsClass;
import static io.opentelemetry.javaagent.instrumentation.thrift.v0_9_1.ThriftSingletons.clientInstrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.thrift.common.RequestScopeContext;
import io.opentelemetry.instrumentation.thrift.common.client.MethodAccessor;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.thrift.v0_9_1.AsyncMethodCallbackWrapper;
import java.util.Set;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.thrift.async.AsyncMethodCallback;
import org.apache.thrift.async.TAsyncMethodCall;
import org.apache.thrift.protocol.TMessageType;
import org.apache.thrift.protocol.TProtocol;

public final class ThriftAsyncWriteArgsInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return extendsClass(named("org.apache.thrift.async.TAsyncMethodCall"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod().and(named("write_args")),
        ThriftAsyncWriteArgsInstrumentation.class.getName() + "$WriteArgsAdvice");
  }

  public static class WriteArgsAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(
        @Advice.Origin("#t") String serviceName, @Advice.Argument(value = 0) TProtocol protocol) {
      if (protocol instanceof ClientOutProtocolWrapper) {
        Set<String> methodNames = MethodAccessor.voidMethodNames(serviceName);
        // Compatible with asynchronous oneway method
        if (methodNames.contains("getResult")) {
          ClientOutProtocolWrapper wrapper = (ClientOutProtocolWrapper) protocol;
          wrapper.setType(TMessageType.ONEWAY);
        }
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.This TAsyncMethodCall<?> methodCall,
        @Advice.Argument(value = 0) TProtocol protocol,
        @Advice.Thrown Throwable throwable) {
      if (protocol instanceof ClientOutProtocolWrapper) {
        ClientOutProtocolWrapper wrapper = (ClientOutProtocolWrapper) protocol;
        RequestScopeContext requestScopeContext = wrapper.getRequestScopeContext();
        if (requestScopeContext == null) {
          return;
        }
        // wrapper.isChangeToOneway() judgment logic is for compatibility with version 0.9.1
        // the return value is void but is not oneway method
        if (throwable != null) {
          requestScopeContext.close();
          Context context = requestScopeContext.getContext();
          clientInstrumenter().end(context, requestScopeContext.getRequest(), null, throwable);
          wrapper.setRequestScopeContext(null);
          return;
        }

        AsyncMethodCallback<?> callback = ASYNC_METHOD_CALLBACK.get(methodCall);
        if (callback instanceof AsyncMethodCallbackWrapper) {
          ((AsyncMethodCallbackWrapper<?>) callback).setRequestScopeContext(requestScopeContext);
        }
      }
    }
  }
}
