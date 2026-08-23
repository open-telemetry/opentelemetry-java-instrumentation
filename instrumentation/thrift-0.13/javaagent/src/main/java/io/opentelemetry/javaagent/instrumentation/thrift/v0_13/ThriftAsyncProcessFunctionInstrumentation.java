/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.thrift.v0_13;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.extendsClass;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.instrumentation.thrift.v0_13.internal.AsyncMethodCallbackUtil;
import io.opentelemetry.instrumentation.thrift.v0_13.internal.ServerInProtocolDecorator;
import io.opentelemetry.instrumentation.thrift.v0_13.internal.ServerOutProtocolDecorator;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.thrift.async.AsyncMethodCallback;
import org.apache.thrift.server.AbstractNonblockingServer;

public final class ThriftAsyncProcessFunctionInstrumentation implements TypeInstrumentation {

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
        named("getResultHandler")
            .and(
                takesArgument(
                    0,
                    named("org.apache.thrift.server.AbstractNonblockingServer$AsyncFrameBuffer"))),
        getClass().getName() + "$GetResultHandlerAdvice");
  }

  @SuppressWarnings("unused")
  public static class GetResultHandlerAdvice {

    @Advice.AssignReturned.ToReturned
    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static AsyncMethodCallback<?> methodExit(
        @Advice.Argument(value = 0) AbstractNonblockingServer.AsyncFrameBuffer fb,
        @Advice.Return AsyncMethodCallback<?> callback) {
      ServerInProtocolDecorator serverInProtocolDecorator =
          (ServerInProtocolDecorator) fb.getInputProtocol();
      ServerOutProtocolDecorator serverOutProtocolDecorator =
          (ServerOutProtocolDecorator) fb.getOutputProtocol();
      return AsyncMethodCallbackUtil.wrap(
          callback, serverInProtocolDecorator, serverOutProtocolDecorator);
    }
  }
}
