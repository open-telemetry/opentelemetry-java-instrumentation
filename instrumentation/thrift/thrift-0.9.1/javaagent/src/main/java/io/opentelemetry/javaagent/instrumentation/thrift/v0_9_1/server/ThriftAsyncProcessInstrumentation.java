/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.thrift.v0_9_1.server;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.extendsClass;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.instrumentation.thrift.common.RequestScopeContext;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.thrift.v0_9_1.AsyncMethodCallbackWrapper;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.thrift.async.AsyncMethodCallback;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.server.AbstractNonblockingServer;

class ThriftAsyncProcessInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return extendsClass(named("org.apache.thrift.AsyncProcessFunction"));
  }

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("org.apache.thrift.AsyncProcessFunction");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("getResultHandler"), getClass().getName() + "$GetResultHandlerAdvice");
  }

  @SuppressWarnings("unused")
  public static class GetResultHandlerAdvice {

    @AssignReturned.ToReturned
    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static AsyncMethodCallback<?> methodExit(
        @Advice.Argument(0) AbstractNonblockingServer.AsyncFrameBuffer fb,
        @Advice.Return AsyncMethodCallback<?> callback) {
      TProtocol inProtocol = fb.getInputProtocol();
      if (inProtocol instanceof ServerInProtocolWrapper) {
        ServerInProtocolWrapper wrapper = (ServerInProtocolWrapper) inProtocol;
        RequestScopeContext requestScopeContext = wrapper.getRequestScopeContext();
        if (requestScopeContext == null) {
          return callback;
        }

        AsyncMethodCallbackWrapper<?> callbackWrapper =
            new AsyncMethodCallbackWrapper<>(callback, true);
        callbackWrapper.setRequestScopeContext(requestScopeContext);
        return callbackWrapper;
      }

      return callback;
    }
  }
}
