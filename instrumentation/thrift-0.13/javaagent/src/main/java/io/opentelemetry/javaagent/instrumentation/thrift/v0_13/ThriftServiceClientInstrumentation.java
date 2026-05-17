/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.thrift.v0_13;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.extendsClass;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.instrumentation.thrift.v0_13.ThriftSingletons.clientInstrumenter;
import static io.opentelemetry.javaagent.instrumentation.thrift.v0_13.ThriftSingletons.getPropagators;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.instrumentation.thrift.v0_13.internal.ClientCallContext;
import io.opentelemetry.instrumentation.thrift.v0_13.internal.ClientProtocolDecorator;
import io.opentelemetry.instrumentation.thrift.v0_13.internal.SocketAccessor;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.thrift.TServiceClient;
import org.apache.thrift.protocol.TProtocol;

class ThriftServiceClientInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return extendsClass(named("org.apache.thrift.TServiceClient"))
        .and(not(named("org.apache.thrift.TServiceClient")));
  }

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("org.apache.thrift.TServiceClient");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isConstructor()
            .and(takesArguments(1))
            .and(takesArgument(0, named("org.apache.thrift.protocol.TProtocol"))),
        getClass().getName() + "$Constructor1Advice");
    transformer.applyAdviceToMethod(
        isConstructor()
            .and(
                takesArgument(0, named("org.apache.thrift.protocol.TProtocol"))
                    .and(takesArgument(1, named("org.apache.thrift.protocol.TProtocol")))),
        getClass().getName() + "$Constructor2Advice");

    transformer.applyAdviceToMethod(
        isMethod().and(not(nameStartsWith("send_"))).and(not(nameStartsWith("recv_"))),
        getClass().getName() + "$MethodAdvice");
  }

  @SuppressWarnings("unused")
  public static class Constructor1Advice {
    @Advice.AssignReturned.ToArguments(@ToArgument(0))
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static TProtocol onEnter(@Advice.Argument(0) TProtocol protocol) {
      return new ClientProtocolDecorator(protocol, getPropagators());
    }
  }

  @SuppressWarnings("unused")
  public static class Constructor2Advice {
    @Advice.AssignReturned.ToArguments(@ToArgument(1))
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static TProtocol onEnter(@Advice.Argument(1) TProtocol outProtocol) {
      return new ClientProtocolDecorator(outProtocol, getPropagators());
    }
  }

  @SuppressWarnings("unused")
  public static class MethodAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    @Nullable
    public static ClientCallContext onEnter(
        @Advice.This TServiceClient client,
        @Advice.Origin("#m") String methodName,
        @Advice.Origin("#t") Class<?> declaringClass) {
      if (ClientCallContext.get() != null) {
        return null;
      }
      return ClientCallContext.start(
          clientInstrumenter(),
          methodName,
          declaringClass,
          SocketAccessor.getSocket(client.getInputProtocol().getTransport()));
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Enter @Nullable ClientCallContext clientContext,
        @Advice.Thrown Throwable throwable) {
      if (clientContext != null) {
        clientContext.close();
        clientContext.endSpan(throwable);
      }
    }
  }
}
