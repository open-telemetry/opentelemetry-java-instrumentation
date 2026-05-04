/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.thrift.v0_13;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.extendsClass;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.instrumentation.thrift.v0_13.ThriftSingletons.getPropagators;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.instrumentation.thrift.v0_13.internal.AsyncMethodCallbackUtil;
import io.opentelemetry.instrumentation.thrift.v0_13.internal.ClientCallContext;
import io.opentelemetry.instrumentation.thrift.v0_13.internal.ClientProtocolDecorator;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
import net.bytebuddy.description.method.ParameterList;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.thrift.async.AsyncMethodCallback;
import org.apache.thrift.protocol.TProtocolFactory;
import org.apache.thrift.transport.TNonblockingTransport;

class ThriftAsyncClientInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return extendsClass(named("org.apache.thrift.async.TAsyncClient"))
        .and(not(named("org.apache.thrift.async.TAsyncClient")));
  }

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("org.apache.thrift.async.TAsyncClient");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isConstructor().and(takesArgument(0, named("org.apache.thrift.protocol.TProtocolFactory"))),
        getClass().getName() + "$ConstructorAdvice");

    transformer.applyAdviceToMethod(
        isMethod()
            .and(
                target -> {
                  ParameterList<?> parameterList = target.getParameters();
                  if (!parameterList.isEmpty()) {
                    String lastParameter =
                        parameterList.get(parameterList.size() - 1).getType().asErasure().getName();
                    return "org.apache.thrift.async.AsyncMethodCallback".equals(lastParameter);
                  }
                  return false;
                }),
        getClass().getName() + "$MethodAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConstructorAdvice {
    @Advice.AssignReturned.ToArguments(@ToArgument(0))
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static TProtocolFactory onEnter(
        @Advice.Origin("#t") Class<?> declaringClass,
        @Advice.Argument(0) TProtocolFactory protocolFactory,
        @Advice.Argument(2) TNonblockingTransport transport) {
      Class<?> serviceClass = declaringClass;
      if (serviceClass.getDeclaringClass() != null) {
        serviceClass = serviceClass.getDeclaringClass();
      }

      return new ClientProtocolDecorator.Factory(
          protocolFactory,
          serviceClass.getName(),
          ThriftSingletons.clientInstrumenter(),
          getPropagators(),
          transport);
    }
  }

  @SuppressWarnings("unused")
  public static class MethodAdvice {

    @Advice.AssignReturned.ToAllArguments(index = 0)
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Object[] onEnter(@Advice.AllArguments Object[] arguments) {
      ClientCallContext clientContext = ClientCallContext.start();
      Object[] args = new Object[arguments.length];
      System.arraycopy(arguments, 0, args, 0, arguments.length);
      if (args[args.length - 1] != null) {
        AsyncMethodCallback<?> callback = (AsyncMethodCallback<?>) args[args.length - 1];
        args[args.length - 1] = AsyncMethodCallbackUtil.wrap(callback, clientContext);
        clientContext.setHasAsyncCallback();
      }
      return new Object[] {args, clientContext};
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Enter Object[] enterResult, @Advice.Thrown Throwable throwable) {
      ClientCallContext clientContext = (ClientCallContext) enterResult[1];
      clientContext.end();
      if (throwable != null) {
        clientContext.endSpan(throwable);
      }
    }
  }
}
